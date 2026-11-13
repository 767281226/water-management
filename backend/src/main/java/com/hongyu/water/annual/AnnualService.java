package com.hongyu.water.annual;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.annual.AnnualDtos.AnnualResponse;
import com.hongyu.water.annual.AnnualDtos.DeptRow;
import com.hongyu.water.annual.AnnualDtos.MonthPoint;
import com.hongyu.water.annual.AnnualDtos.PersonRow;
import com.hongyu.water.annual.AnnualDtos.Tiles;
import com.hongyu.water.common.BizException;
import com.hongyu.water.project.Project;
import com.hongyu.water.project.ProjectRepository;
import com.hongyu.water.project.ProjectStatus;
import com.hongyu.water.score.Score;
import com.hongyu.water.score.ScoreDtos.Share;
import com.hongyu.water.score.ScoreRepository;
import com.hongyu.water.score.ScoreService;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.DataScopeHelper;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.dept.SysDept;
import com.hongyu.water.system.dept.SysDeptRepository;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.system.user.UserStatus;
import com.hongyu.water.weekly.WeekRules;
import com.hongyu.water.weekly.WeeklyItemRepository;
import com.hongyu.water.weekly.WeeklyPlan;
import com.hongyu.water.weekly.WeeklyPlanRepository;
import com.hongyu.water.weekly.WeeklyService;

/**
 * 年度汇总（需求 3.3.6）：只统计已发布的考评结果。数据范围与周报统计一致。
 */
@Service
public class AnnualService {

    private final ProjectRepository projectRepo;
    private final ScoreRepository scoreRepo;
    private final ScoreService scoreService;
    private final SysUserRepository userRepo;
    private final SysDeptRepository deptRepo;
    private final DeptService deptService;
    private final WeeklyItemRepository itemRepo;
    private final WeeklyPlanRepository planRepo;
    private final DataScopeHelper scope;

    public AnnualService(ProjectRepository projectRepo, ScoreRepository scoreRepo, ScoreService scoreService,
            SysUserRepository userRepo, SysDeptRepository deptRepo, DeptService deptService,
            WeeklyItemRepository itemRepo, WeeklyPlanRepository planRepo, DataScopeHelper scope) {
        this.projectRepo = projectRepo;
        this.scoreRepo = scoreRepo;
        this.scoreService = scoreService;
        this.userRepo = userRepo;
        this.deptRepo = deptRepo;
        this.deptService = deptService;
        this.itemRepo = itemRepo;
        this.planRepo = planRepo;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public AnnualResponse summary(Integer yearParam, Long deptId, CurrentUser cu) {
        int year = yearParam == null ? LocalDate.now().getYear() : yearParam;
        if (year < 2000 || year > 2100) {
            throw new BizException("年份不正确");
        }
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        LocalDate dec31 = LocalDate.of(year, 12, 31);
        Set<Long> visible = scope.visibleDeptIds(cu);
        Set<Long> filter = deptId == null ? null : deptService.selfAndDescendants(deptId);
        boolean self = cu.dataScope() == DataScope.SELF;

        // ---- 人员范围
        Map<Long, String> deptNames = deptService.nameMap();
        List<SysUser> users = userRepo.findByDeletedFalseAndStatusOrderByIdAsc(UserStatus.ENABLED).stream()
                .filter(u -> !u.isBuiltin() && WeeklyService.shouldFill(u))
                .filter(u -> self ? u.getId().equals(cu.id()) : visible == null || visible.contains(u.getDeptId()))
                .filter(u -> filter == null || filter.contains(u.getDeptId()))
                .toList();

        // ---- 年内存档项目（范围内）
        List<Project> archived = projectRepo.findByDeletedFalseAndStatusAndArchivedAtBetween(ProjectStatus.ARCHIVED,
                jan1.atStartOfDay(), dec31.plusDays(1).atStartOfDay()).stream()
                .filter(p -> self ? p.involves(cu.id()) : p.involves(cu.id()) || scope.canSeeDept(cu, p.getDeptId()))
                .filter(p -> filter == null || filter.contains(p.getDeptId()))
                .toList();

        // ---- 年内已发布考评
        List<Score> published = scoreRepo.findByMonthStartingWithAndStatus(year + "-", Score.Status.PUBLISHED);
        Map<Long, Project> scoreProjects = published.isEmpty() ? Map.of()
                : projectRepo.findByIdIn(published.stream().map(Score::getProjectId).toList()).stream()
                        .collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<Long, Score> scoreByProject = published.stream().collect(Collectors.toMap(Score::getProjectId, s -> s));

        // ---- 总览
        List<BigDecimal> scopeTotals = archived.stream().map(p -> scoreByProject.get(p.getId())).filter(Objects::nonNull)
                .map(Score::getTotal).toList();
        long onTime = archived.stream().filter(p -> p.getActualEndDate() != null && !p.getActualEndDate().isAfter(p.getPlanEndDate())).count();

        // ---- 月度分布
        List<MonthPoint> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            int month = m;
            List<Project> ps = archived.stream().filter(p -> p.getArchivedAt().getMonthValue() == month).toList();
            List<BigDecimal> totals = ps.stream().map(p -> scoreByProject.get(p.getId())).filter(Objects::nonNull)
                    .map(Score::getTotal).toList();
            months.add(new MonthPoint(m, ps.size(), avg(totals)));
        }

        // ---- 个人得分：每月按个人贡献加权平均
        Map<Long, String> names = users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        Map<Long, double[]> sum = new HashMap<>(), weight = new HashMap<>();
        Map<Long, Integer> projectCount = new HashMap<>();
        for (Score s : published) {
            Project p = scoreProjects.get(s.getProjectId());
            if (p == null || s.getTotal() == null) {
                continue;
            }
            int mi = Integer.parseInt(s.getMonth().substring(5)) - 1;
            for (Share sh : scoreService.shares(p, names)) {
                if (!names.containsKey(sh.userId()) || sh.ratio() <= 0) {
                    continue;
                }
                sum.computeIfAbsent(sh.userId(), k -> new double[12])[mi] += s.getTotal().doubleValue() * sh.ratio();
                weight.computeIfAbsent(sh.userId(), k -> new double[12])[mi] += sh.ratio();
                projectCount.merge(sh.userId(), 1, Integer::sum);
            }
        }

        // ---- 工时、周报填报率
        // 以周一所在年份归属周次：第一个周一 ~ 最后一个周一
        LocalDate firstMonday = jan1.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = dec31.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<Long, BigDecimal> hours = new HashMap<>();
        itemRepo.sumDaysByUserBetween(firstMonday, lastMonday).forEach(r -> hours.put((Long) r[0], (BigDecimal) r[1]));
        LocalDate endMonday = WeekRules.mondayOf(LocalDate.now()).isBefore(lastMonday) ? WeekRules.mondayOf(LocalDate.now()) : lastMonday;
        Map<Long, Long> submitted = new HashMap<>();
        planRepo.countByStatusAndUserBetween(WeeklyPlan.PlanStatus.SUBMITTED, firstMonday, endMonday)
                .forEach(r -> submitted.put((Long) r[0], (Long) r[1]));

        List<PersonRow> persons = new ArrayList<>();
        long totalExpected = 0, totalSubmitted = 0;
        for (SysUser u : users) {
            List<BigDecimal> monthly = new ArrayList<>();
            double[] su = sum.get(u.getId()), we = weight.get(u.getId());
            for (int m = 0; m < 12; m++) {
                monthly.add(we != null && we[m] > 0 ? BigDecimal.valueOf(su[m] / we[m]).setScale(1, RoundingMode.HALF_UP) : null);
            }
            LocalDate start = WeekRules.mondayOf(u.getCreatedAt().toLocalDate());
            if (start.isBefore(firstMonday)) {
                start = firstMonday;
            }
            long expected = endMonday.isBefore(start) ? 0 : ChronoUnit.WEEKS.between(start, endMonday) + 1;
            long done = Math.min(submitted.getOrDefault(u.getId(), 0L), expected);
            totalExpected += expected;
            totalSubmitted += done;
            persons.add(new PersonRow(u.getId(), u.getRealName(), u.getEmployeeNo(), deptNames.get(u.getDeptId()),
                    projectCount.getOrDefault(u.getId(), 0), hours.getOrDefault(u.getId(), BigDecimal.ZERO),
                    expected == 0 ? null : (int) Math.round(done * 100.0 / expected), monthly,
                    avg(monthly.stream().filter(Objects::nonNull).toList()), null));
        }
        // 排名：按年度平均分，无得分的排在最后
        persons.sort(Comparator.comparing(PersonRow::avg, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PersonRow::userId));
        List<PersonRow> ranked = new ArrayList<>();
        int rank = 0;
        for (PersonRow r : persons) {
            ranked.add(r.avg() == null ? r : new PersonRow(r.userId(), r.name(), r.employeeNo(), r.deptName(), r.projects(),
                    r.hours(), r.fillRate(), r.monthly(), r.avg(), ++rank));
        }

        // ---- 部门对比
        List<DeptRow> depts = new ArrayList<>();
        if (!self) {
            Map<Long, List<PersonRow>> byDept = new LinkedHashMap<>();
            Map<Long, Long> userDept = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u.getDeptId() == null ? -1L : u.getDeptId()));
            ranked.forEach(r -> byDept.computeIfAbsent(userDept.get(r.userId()), k -> new ArrayList<>()).add(r));
            for (SysDept d : deptRepo.findAllByOrderBySortAscIdAsc()) {
                List<PersonRow> rows = byDept.getOrDefault(d.getId(), List.of());
                List<Project> ps = archived.stream().filter(p -> d.getId().equals(p.getDeptId())).toList();
                if (rows.isEmpty() && ps.isEmpty()) {
                    continue;
                }
                List<BigDecimal> totals = ps.stream().map(p -> scoreByProject.get(p.getId())).filter(Objects::nonNull).map(Score::getTotal).toList();
                List<Integer> fills = rows.stream().map(PersonRow::fillRate).filter(Objects::nonNull).toList();
                depts.add(new DeptRow(d.getId(), d.getName(), rows.size(), ps.size(), avg(totals),
                        fills.isEmpty() ? null : (int) Math.round(fills.stream().mapToInt(Integer::intValue).average().orElse(0))));
            }
        }

        PersonRow me = ranked.stream().filter(r -> r.userId().equals(cu.id())).findFirst().orElse(null);
        Tiles tiles = new Tiles(archived.size(), scopeTotals.size(), avg(scopeTotals),
                archived.isEmpty() ? null : (int) Math.round(onTime * 100.0 / archived.size()),
                totalExpected == 0 ? null : (int) Math.round(totalSubmitted * 100.0 / totalExpected), users.size());
        String scopeLabel = self ? "本人" : visible == null ? "全公司" : "本部门";
        return new AnnualResponse(year, scopeLabel, tiles, months, ranked, depts, me);
    }

    private static BigDecimal avg(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }
}
