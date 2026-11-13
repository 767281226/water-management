package com.hongyu.water.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.project.Project;
import com.hongyu.water.project.ProjectRepository;
import com.hongyu.water.score.ScoreDtos.GradeItem;
import com.hongyu.water.score.ScoreDtos.GradeRequest;
import com.hongyu.water.score.ScoreDtos.ItemVO;
import com.hongyu.water.score.ScoreDtos.MonthResponse;
import com.hongyu.water.score.ScoreDtos.ProjectBrief;
import com.hongyu.water.score.ScoreDtos.Reference;
import com.hongyu.water.score.ScoreDtos.RuleRef;
import com.hongyu.water.score.ScoreDtos.ScoreActions;
import com.hongyu.water.score.ScoreDtos.ScoreVO;
import com.hongyu.water.score.ScoreDtos.Share;
import com.hongyu.water.score.ScoreDtos.TodoResponse;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.DataScopeHelper;
import com.hongyu.water.system.config.ConfigDtos.ScoreRuleDto;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.weekly.WeeklyItemRepository;

/**
 * 月度考评（需求 3.3.5）。
 * <ul>
 * <li>项目确认存档后自动进入存档当月的考评清单；</li>
 * <li>部门负责人（score:grade）在数据范围内打分 → 公司领导（score:publish）审核发布；发布后锁定，可撤回；</li>
 * <li>员工只能看到自己参与项目的已发布结果；</li>
 * <li>个人贡献按周报工时占比分配（无周报数据时负责人 40%，其余成员平分）。</li>
 * </ul>
 */
@Service
public class ScoreService {

    static final String PERM_GRADE = "score:grade";
    static final String PERM_PUBLISH = "score:publish";

    private final ScoreRepository repo;
    private final ProjectRepository projectRepo;
    private final WeeklyItemRepository itemRepo;
    private final SysUserRepository userRepo;
    private final DeptService deptService;
    private final ConfigService configService;
    private final DataScopeHelper scope;

    public ScoreService(ScoreRepository repo, ProjectRepository projectRepo, WeeklyItemRepository itemRepo,
            SysUserRepository userRepo, DeptService deptService, ConfigService configService, DataScopeHelper scope) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.deptService = deptService;
        this.configService = configService;
        this.scope = scope;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public MonthResponse month(String month, CurrentUser cu) {
        String m = normalizeMonth(month);
        List<Score> scores = repo.findByMonthOrderByIdAsc(m);
        Map<Long, Project> projects = projects(scores.stream().map(Score::getProjectId).toList());
        List<ScoreVO> list = scores.stream().filter(s -> visible(projects.get(s.getProjectId()), cu))
                .map(s -> toVO(s, projects.get(s.getProjectId()), cu)).toList();
        int pending = (int) list.stream().filter(v -> v.status() == Score.Status.PENDING).count();
        int scored = (int) list.stream().filter(v -> v.status() == Score.Status.SCORED).count();
        int published = (int) list.stream().filter(v -> v.status() == Score.Status.PUBLISHED).count();
        return new MonthResponse(m, pending, scored, published, enabledRules(), list);
    }

    @Transactional(readOnly = true)
    public ScoreVO detail(Long id, CurrentUser cu) {
        Score s = mustGet(id);
        Project p = projectRepo.findById(s.getProjectId()).orElseThrow(() -> BizException.notFound("项目"));
        if (!visible(p, cu)) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有查看该考评的权限");
        }
        return toVO(s, p, cu);
    }

    /** 工作台待办：待我打分 / 待我发布 */
    @Transactional(readOnly = true)
    public TodoResponse todo(CurrentUser cu) {
        List<Score> open = repo.findAll().stream().filter(s -> !s.published()).toList();
        Map<Long, Project> projects = projects(open.stream().map(Score::getProjectId).toList());
        int grade = 0, publish = 0;
        for (Score s : open) {
            ScoreActions a = actions(s, projects.get(s.getProjectId()), cu);
            if (a.grade() && s.getStatus() == Score.Status.PENDING) {
                grade++;
            }
            if (a.publish()) {
                publish++;
            }
        }
        return new TodoResponse(grade, publish);
    }

    public List<RuleRef> enabledRules() {
        return configService.scoreRules().stream().filter(ScoreRuleDto::enabled)
                .map(r -> new RuleRef(r.ruleKey(), r.name(), r.weight(), r.description())).toList();
    }

    // ------------------------------------------------------------------ 打分 / 发布

    @Transactional
    public ScoreVO grade(Long id, GradeRequest req, CurrentUser cu) {
        Score s = mustGet(id);
        Project p = projectRepo.findById(s.getProjectId()).orElseThrow(() -> BizException.notFound("项目"));
        if (s.published()) {
            throw new BizException("考评结果已发布，不能修改；如需更正请先撤回发布");
        }
        if (!actions(s, p, cu).grade()) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有给该项目打分的权限");
        }
        List<RuleRef> rules = enabledRules();
        Map<String, Integer> given = new HashMap<>();
        for (GradeItem it : req.items()) {
            given.put(it.ruleKey(), it.score());
        }
        s.getItems().clear();
        BigDecimal total = BigDecimal.ZERO;
        int sort = 0;
        for (RuleRef r : rules) {
            Integer v = given.get(r.ruleKey());
            if (v == null) {
                throw new BizException("请填写「" + r.name() + "」的得分");
            }
            ScoreItem item = new ScoreItem();
            item.setScore(s);
            item.setRuleKey(r.ruleKey());
            item.setRuleName(r.name());
            item.setWeight(r.weight());
            item.setValue(v);
            item.setSort(++sort);
            s.getItems().add(item);
            total = total.add(BigDecimal.valueOf((long) v * r.weight()));
        }
        s.setTotal(total.divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP));
        s.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        s.setStatus(Score.Status.SCORED);
        s.setScorerId(cu.id());
        s.setScoredAt(LocalDateTime.now().withNano(0));
        repo.saveAndFlush(s);
        return toVO(s, p, cu);
    }

    @Transactional
    public void publish(Long id, CurrentUser cu) {
        Score s = mustGet(id);
        Project p = projectRepo.findById(s.getProjectId()).orElseThrow(() -> BizException.notFound("项目"));
        if (s.getStatus() != Score.Status.SCORED) {
            throw new BizException(s.published() ? "已经发布" : "尚未打分，不能发布");
        }
        if (!actions(s, p, cu).publish()) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有发布该考评的权限");
        }
        doPublish(s, cu);
    }

    /** 发布某月全部已打分且有权限发布的考评，返回发布数量。 */
    @Transactional
    public int publishMonth(String month, CurrentUser cu) {
        List<Score> scores = repo.findByMonthOrderByIdAsc(normalizeMonth(month));
        Map<Long, Project> projects = projects(scores.stream().map(Score::getProjectId).toList());
        int n = 0;
        for (Score s : scores) {
            if (actions(s, projects.get(s.getProjectId()), cu).publish()) {
                doPublish(s, cu);
                n++;
            }
        }
        if (n == 0) {
            throw new BizException("没有可以发布的考评（需先打分）");
        }
        return n;
    }

    @Transactional
    public void unpublish(Long id, CurrentUser cu) {
        Score s = mustGet(id);
        Project p = projectRepo.findById(s.getProjectId()).orElseThrow(() -> BizException.notFound("项目"));
        if (!actions(s, p, cu).unpublish()) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有撤回该考评的权限，或考评尚未发布");
        }
        s.setStatus(Score.Status.SCORED);
        s.setPublisherId(null);
        s.setPublishedAt(null);
    }

    private void doPublish(Score s, CurrentUser cu) {
        s.setStatus(Score.Status.PUBLISHED);
        s.setPublisherId(cu.id());
        s.setPublishedAt(LocalDateTime.now().withNano(0));
    }

    // ------------------------------------------------------------------ 统计（年度汇总也会用到）

    /** 个人贡献分配。 */
    @Transactional(readOnly = true)
    public List<Share> shares(Project p, Map<Long, String> names) {
        Map<Long, BigDecimal> days = new LinkedHashMap<>();
        for (Object[] row : itemRepo.sumDaysByUserForProject(p.getId())) {
            days.put((Long) row[0], (BigDecimal) row[1]);
        }
        Set<Long> people = new LinkedHashSet<>();
        people.add(p.getLeaderId());
        people.addAll(p.getMemberIds().stream().sorted().toList());
        people.addAll(days.keySet());
        BigDecimal total = days.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Share> result = new ArrayList<>();
        if (total.signum() > 0) {
            for (Long u : people) {
                BigDecimal d = days.getOrDefault(u, BigDecimal.ZERO);
                result.add(new Share(u, names.get(u), d, d.doubleValue() / total.doubleValue(), true));
            }
        } else {
            int others = people.size() - 1;
            for (Long u : people) {
                double ratio = others == 0 ? 1.0 : u.equals(p.getLeaderId()) ? 0.4 : 0.6 / others;
                result.add(new Share(u, names.get(u), BigDecimal.ZERO, ratio, false));
            }
        }
        result.sort((a, b) -> Double.compare(b.ratio(), a.ratio()));
        return result;
    }

    Reference reference(Project p) {
        LocalDate start = p.getStartDate() != null ? p.getStartDate() : p.getCreatedAt().toLocalDate();
        LocalDate end = p.getActualEndDate() != null ? p.getActualEndDate()
                : p.getArchivedAt() != null ? p.getArchivedAt().toLocalDate() : null;
        Integer plan = (int) ChronoUnit.DAYS.between(start, p.getPlanEndDate());
        Integer actual = end == null ? null : (int) ChronoUnit.DAYS.between(start, end);
        Integer delay = end == null ? null : (int) ChronoUnit.DAYS.between(p.getPlanEndDate(), end);
        Double avg = itemRepo.avgCompletionByProject(p.getId());
        return new Reference(plan, actual, delay, itemRepo.countByProjectId(p.getId()), itemRepo.sumDaysByProject(p.getId()),
                avg == null ? null : (int) Math.round(avg), itemRepo.countOverduePlansByProject(p.getId()));
    }

    // ------------------------------------------------------------------ 内部

    private boolean manager(CurrentUser cu) {
        return (has(cu, PERM_GRADE) || has(cu, PERM_PUBLISH)) && cu.dataScope() != DataScope.SELF;
    }

    private boolean visible(Project p, CurrentUser cu) {
        return p != null && (p.involves(cu.id()) || (manager(cu) && scope.canSeeDept(cu, p.getDeptId())));
    }

    private ScoreActions actions(Score s, Project p, CurrentUser cu) {
        if (p == null || cu.dataScope() == DataScope.SELF || !scope.canSeeDept(cu, p.getDeptId())) {
            return new ScoreActions(false, false, false);
        }
        return new ScoreActions(has(cu, PERM_GRADE) && !s.published(),
                has(cu, PERM_PUBLISH) && s.getStatus() == Score.Status.SCORED,
                has(cu, PERM_PUBLISH) && s.published());
    }

    private ScoreVO toVO(Score s, Project p, CurrentUser cu) {
        Set<Long> ids = new HashSet<>(p.getMemberIds());
        ids.add(p.getLeaderId());
        if (s.getScorerId() != null) {
            ids.add(s.getScorerId());
        }
        if (s.getPublisherId() != null) {
            ids.add(s.getPublisherId());
        }
        itemRepo.sumDaysByUserForProject(p.getId()).forEach(r -> ids.add((Long) r[0]));
        Map<Long, String> names = userRepo.findByIdIn(ids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        boolean detail = s.published() || (manager(cu) && scope.canSeeDept(cu, p.getDeptId()));
        ProjectBrief brief = new ProjectBrief(p.getId(), p.getCode(), p.getName(), p.getType(), deptService.nameOf(p.getDeptId()),
                names.get(p.getLeaderId()), p.getArchivedAt(), p.getStartDate(), p.getPlanEndDate(), p.getActualEndDate());
        List<ItemVO> items = detail ? s.getItems().stream()
                .map(i -> new ItemVO(i.getRuleKey(), i.getRuleName(), i.getWeight(), i.getValue())).toList() : List.of();
        return new ScoreVO(s.getId(), s.getMonth(), s.getStatus(), brief, detail, detail ? s.getTotal() : null, items,
                detail ? s.getComment() : null, names.get(s.getScorerId()), s.getScoredAt(), names.get(s.getPublisherId()),
                s.getPublishedAt(), reference(p), shares(p, names), actions(s, p, cu));
    }

    private Map<Long, Project> projects(List<Long> ids) {
        return ids.isEmpty() ? Map.of()
                : projectRepo.findByIdIn(ids).stream().collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private Score mustGet(Long id) {
        return repo.findWithItemsById(id).orElseThrow(() -> BizException.notFound("考评记录"));
    }

    static String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            LocalDate d = LocalDate.now().minusMonths(1);
            return String.format("%d-%02d", d.getYear(), d.getMonthValue());
        }
        if (!month.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new BizException("月份格式应为 YYYY-MM");
        }
        return month;
    }

    static boolean has(CurrentUser cu, String perm) {
        return cu.superAdmin() || cu.permissions().contains(perm);
    }
}
