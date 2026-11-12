package com.hongyu.water.weekly;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.project.Project;
import com.hongyu.water.project.ProjectRepository;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.DataScopeHelper;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.role.SysRole;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.system.user.UserStatus;
import com.hongyu.water.weekly.WeeklyDtos.ExtraItemForm;
import com.hongyu.water.weekly.WeeklyDtos.HistoryRow;
import com.hongyu.water.weekly.WeeklyDtos.ItemVO;
import com.hongyu.water.weekly.WeeklyDtos.MineResponse;
import com.hongyu.water.weekly.WeeklyDtos.PlanItemForm;
import com.hongyu.water.weekly.WeeklyDtos.PlanVO;
import com.hongyu.water.weekly.WeeklyDtos.ReportItemForm;
import com.hongyu.water.weekly.WeeklyDtos.SavePlanRequest;
import com.hongyu.water.weekly.WeeklyDtos.StatResponse;
import com.hongyu.water.weekly.WeeklyDtos.StatRow;
import com.hongyu.water.weekly.WeeklyDtos.SubmitReportRequest;
import com.hongyu.water.weekly.WeeklyDtos.TodoResponse;
import com.hongyu.water.weekly.WeeklyPlan.PlanStatus;
import com.hongyu.water.weekly.WeeklyPlan.ReportStatus;

import org.springframework.data.domain.Sort;

/**
 * 周计划 / 周完成情况（需求 3.3.2 ~ 3.3.4）。
 * <ul>
 * <li>可填报上周（补填）、本周、下周（提前填）的计划；被退回的周报不受限制；</li>
 * <li>完成情况提交前计划可以修改；提交后锁定，部门负责人退回后可重新填写；</li>
 * <li>超过参数配置中的截止时间提交，标记为逾期；</li>
 * <li>完成率 = 计划事项完成比例的平均值（计划外工作不计入）。</li>
 * </ul>
 */
@Service
public class WeeklyService {

    private static final String PERM_STAT = "weekly:stat";
    private static final String PERM_COMMENT = "weekly:stat:comment";
    private static final String PERM_FILL = "weekly:plan";

    private final WeeklyPlanRepository planRepo;
    private final ProjectRepository projectRepo;
    private final SysUserRepository userRepo;
    private final DeptService deptService;
    private final DataScopeHelper scope;
    private final WeekRules rules;

    public WeeklyService(WeeklyPlanRepository planRepo, ProjectRepository projectRepo, SysUserRepository userRepo,
            DeptService deptService, DataScopeHelper scope, WeekRules rules) {
        this.planRepo = planRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
        this.deptService = deptService;
        this.scope = scope;
        this.rules = rules;
    }

    // ------------------------------------------------------------------ 我的周报

    @Transactional(readOnly = true)
    public MineResponse mine(CurrentUser cu, LocalDate weekStart) {
        LocalDate week = normalize(weekStart);
        Optional<WeeklyPlan> plan = planRepo.findByUserIdAndWeekStart(cu.id(), week);
        String locked = planLockedReason(week, plan.orElse(null));
        String reportLocked = reportLockedReason(week, plan.orElse(null));
        List<ItemVO> unfinished = planRepo.findByUserIdAndWeekStart(cu.id(), week.minusWeeks(1))
                .filter(WeeklyPlan::reported)
                .map(p -> toItems(p.getItems().stream().filter(i -> !i.isExtra() && i.getCompletion() != null
                        && i.getCompletion() < 100).toList()))
                .orElse(List.of());
        return new MineResponse(rules.info(week), plan.map(this::toVO).orElse(null), locked == null,
                reportLocked == null, locked != null ? locked : reportLocked, unfinished);
    }

    @Transactional
    public PlanVO savePlan(CurrentUser cu, SavePlanRequest req) {
        LocalDate week = normalize(req.weekStart());
        WeeklyPlan plan = planRepo.findByUserIdAndWeekStart(cu.id(), week).orElse(null);
        String locked = planLockedReason(week, plan);
        if (locked != null) {
            throw new BizException(locked);
        }
        Map<Long, Project> projects = checkProjects(cu, req.items().stream().map(PlanItemForm::projectId).toList());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (plan == null) {
            plan = new WeeklyPlan();
            plan.setUserId(cu.id());
            plan.setWeekStart(week);
            plan.setPlanSubmittedAt(now);
            plan.setPlanOverdue(now.isAfter(rules.planDeadline(week)));
        }
        plan.setPlanStatus(PlanStatus.SUBMITTED);
        plan.setReturnReason(null);
        plan.setReportStatus(ReportStatus.NOT_SUBMITTED);
        plan.setReportSubmittedAt(null);
        plan.setCompletionRate(null);
        plan.getItems().clear();
        int sort = 0;
        for (PlanItemForm f : req.items()) {
            WeeklyItem it = new WeeklyItem();
            it.setPlan(plan);
            it.setProjectId(f.projectId() != null && projects.containsKey(f.projectId()) ? f.projectId() : null);
            it.setContent(f.content().trim());
            it.setTarget(blankToNull(f.target()));
            it.setPlanDays(f.planDays());
            it.setRemark(blankToNull(f.remark()));
            it.setSort(++sort);
            plan.getItems().add(it);
        }
        return toVO(planRepo.saveAndFlush(plan));
    }

    @Transactional
    public PlanVO submitReport(CurrentUser cu, SubmitReportRequest req) {
        LocalDate week = normalize(req.weekStart());
        WeeklyPlan plan = planRepo.findByUserIdAndWeekStart(cu.id(), week)
                .orElseThrow(() -> new BizException("本周还没有提交计划，请先填写周计划"));
        String locked = reportLockedReason(week, plan);
        if (locked != null) {
            throw new BizException(locked);
        }
        // 计划事项：逐条填写完成情况
        plan.getItems().removeIf(WeeklyItem::isExtra);
        Map<Long, ReportItemForm> forms = req.items().stream()
                .collect(Collectors.toMap(ReportItemForm::id, Function.identity(), (a, b) -> a));
        int idx = 0;
        for (WeeklyItem it : plan.getItems()) {
            idx++;
            ReportItemForm f = forms.get(it.getId());
            if (f == null) {
                throw new BizException("请填写事项 " + idx + "「" + it.getContent() + "」的完成情况");
            }
            if (f.completion() < 100 && (f.reason() == null || f.reason().isBlank())) {
                throw new BizException("事项 " + idx + " 未全部完成，请填写未完成原因");
            }
            it.setCompletion(f.completion());
            it.setActualContent(blankToNull(f.actualContent()));
            it.setActualDays(f.actualDays() != null ? f.actualDays() : it.getPlanDays());
            it.setReason(f.completion() < 100 ? f.reason().trim() : null);
        }
        // 计划外工作
        List<ExtraItemForm> extras = req.extras() == null ? List.of() : req.extras();
        Map<Long, Project> projects = checkProjects(cu, extras.stream().map(ExtraItemForm::projectId).toList());
        int sort = plan.getItems().size();
        for (ExtraItemForm f : extras) {
            WeeklyItem it = new WeeklyItem();
            it.setPlan(plan);
            it.setExtra(true);
            it.setProjectId(f.projectId() != null && projects.containsKey(f.projectId()) ? f.projectId() : null);
            it.setContent(f.content().trim());
            it.setActualDays(f.actualDays());
            it.setSort(++sort);
            plan.getItems().add(it);
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        plan.setReportStatus(ReportStatus.SUBMITTED);
        plan.setReportSubmittedAt(now);
        plan.setReportOverdue(now.isAfter(rules.reportDeadline(week)));
        plan.setCompletionRate(rate(plan));
        return toVO(planRepo.saveAndFlush(plan));
    }

    @Transactional(readOnly = true)
    public PageResult<HistoryRow> history(CurrentUser cu, Integer page, Integer size) {
        var p = planRepo.findByUserIdOrderByWeekStartDesc(cu.id(), PageQuery.of(page, size, Sort.unsorted()));
        return PageResult.of(p, w -> new HistoryRow(w.getId(), rules.info(w.getWeekStart()),
                (int) w.getItems().stream().filter(i -> !i.isExtra()).count(), planDays(w), w.isPlanOverdue(),
                w.getPlanStatus(), w.getReportStatus(), w.isReportOverdue(), w.getCompletionRate(), w.getComment()));
    }

    @Transactional(readOnly = true)
    public TodoResponse todo(CurrentUser cu) {
        LocalDate week = rules.currentMonday();
        Optional<WeeklyPlan> plan = planRepo.findByUserIdAndWeekStart(cu.id(), week);
        LocalDateTime now = LocalDateTime.now();
        return new TodoResponse(rules.info(week), plan.map(p -> p.getPlanStatus().name()).orElse("NONE"),
                plan.map(p -> p.getReportStatus().name()).orElse(ReportStatus.NOT_SUBMITTED.name()),
                now.isAfter(rules.planDeadline(week)), now.isAfter(rules.reportDeadline(week)),
                plan.map(WeeklyPlan::getReturnReason).orElse(null));
    }

    // ------------------------------------------------------------------ 统计 / 点评

    @Transactional(readOnly = true)
    public StatResponse stat(CurrentUser cu, LocalDate weekStart, Long deptId) {
        LocalDate week = normalize(weekStart);
        Set<Long> visible = scope.visibleDeptIds(cu);
        Set<Long> filter = deptId == null ? null : deptService.selfAndDescendants(deptId);
        Map<Long, String> deptNames = deptService.nameMap();
        List<SysUser> users = userRepo.findByDeletedFalseAndStatusOrderByIdAsc(UserStatus.ENABLED).stream()
                .filter(u -> !u.isBuiltin() && shouldFill(u))
                .filter(u -> cu.dataScope() == DataScope.SELF ? u.getId().equals(cu.id())
                        : visible == null || visible.contains(u.getDeptId()))
                .filter(u -> filter == null || filter.contains(u.getDeptId()))
                .sorted(Comparator.comparing((SysUser u) -> deptNames.getOrDefault(u.getDeptId(), "~"))
                        .thenComparing(SysUser::getId))
                .toList();
        Map<Long, WeeklyPlan> plans = users.isEmpty() ? Map.of()
                : planRepo.findByWeekStartAndUserIdIn(week, users.stream().map(SysUser::getId).toList()).stream()
                        .collect(Collectors.toMap(WeeklyPlan::getUserId, Function.identity()));
        List<StatRow> rows = users.stream().map(u -> {
            WeeklyPlan p = plans.get(u.getId());
            return p == null
                    ? new StatRow(u.getId(), u.getRealName(), u.getEmployeeNo(), deptNames.get(u.getDeptId()), null,
                            "NONE", false, ReportStatus.NOT_SUBMITTED, false, null, null, 0, false)
                    : new StatRow(u.getId(), u.getRealName(), u.getEmployeeNo(), deptNames.get(u.getDeptId()), p.getId(),
                            p.getPlanStatus().name(), p.isPlanOverdue(), p.getReportStatus(), p.isReportOverdue(),
                            p.getCompletionRate(), planDays(p), (int) p.getItems().stream().filter(i -> !i.isExtra()).count(),
                            p.getComment() != null);
        }).toList();
        int submitted = (int) rows.stream().filter(r -> "SUBMITTED".equals(r.planStatus())).count();
        int overdue = (int) rows.stream().filter(StatRow::planOverdue).count();
        int reported = (int) rows.stream().filter(r -> r.reportStatus() == ReportStatus.SUBMITTED).count();
        Integer avg = rows.stream().map(StatRow::completionRate).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().stream().mapToObj(a -> (int) Math.round(a)).findFirst().orElse(null);
        return new StatResponse(rules.info(week), rows.size(), submitted, overdue, reported, avg, rows);
    }

    @Transactional(readOnly = true)
    public PlanVO detail(CurrentUser cu, Long id) {
        WeeklyPlan p = planRepo.findWithItemsById(id).orElseThrow(() -> BizException.notFound("周报"));
        if (!p.getUserId().equals(cu.id()) && !canManage(cu, p, PERM_STAT)) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有查看该周报的权限");
        }
        return toVO(p);
    }

    @Transactional
    public void comment(CurrentUser cu, Long id, String comment) {
        WeeklyPlan p = manageable(cu, id);
        p.setComment(blankToNull(comment));
        p.setCommentBy(p.getComment() == null ? null : cu.id());
        p.setCommentAt(p.getComment() == null ? null : LocalDateTime.now().withNano(0));
    }

    /** 退回后员工需要重新提交计划和完成情况。 */
    @Transactional
    public void returnPlan(CurrentUser cu, Long id, String reason) {
        WeeklyPlan p = manageable(cu, id);
        if (p.getPlanStatus() == PlanStatus.RETURNED) {
            throw new BizException("该周报已退回，等待员工重新提交");
        }
        p.setPlanStatus(PlanStatus.RETURNED);
        p.setReturnReason(reason.trim());
        p.setReportStatus(ReportStatus.NOT_SUBMITTED);
        p.setReportSubmittedAt(null);
        p.setCompletionRate(null);
    }

    // ------------------------------------------------------------------ 规则

    /** 计划不可编辑的原因；null 表示可以编辑。 */
    private String planLockedReason(LocalDate week, WeeklyPlan plan) {
        if (plan != null && plan.getPlanStatus() == PlanStatus.RETURNED) {
            return null;
        }
        if (plan != null && plan.reported()) {
            return "本周完成情况已提交，计划已锁定";
        }
        long offset = (week.toEpochDay() - rules.currentMonday().toEpochDay()) / 7;
        if (offset < -1) {
            return "只能填写上周、本周和下周的计划";
        }
        if (offset > 1) {
            return "最多只能提前填写下周的计划";
        }
        return null;
    }

    /** 完成情况不可提交的原因；null 表示可以提交。 */
    private String reportLockedReason(LocalDate week, WeeklyPlan plan) {
        if (plan == null) {
            return "请先填写本周计划";
        }
        if (plan.getPlanStatus() == PlanStatus.RETURNED) {
            return "周报已被退回，请先修改并重新提交计划";
        }
        if (plan.reported()) {
            return "完成情况已提交";
        }
        long offset = (week.toEpochDay() - rules.currentMonday().toEpochDay()) / 7;
        if (offset > 0) {
            return "该周尚未开始，暂不能填写完成情况";
        }
        if (offset < -1) {
            return "只能补填上周的完成情况";
        }
        return null;
    }

    /** 校验关联项目：必须是本人负责 / 参与且未存档、未终止的项目。 */
    private Map<Long, Project> checkProjects(CurrentUser cu, List<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) {
            return Map.of();
        }
        Map<Long, Project> map = projectRepo.findByIdIn(set).stream().filter(p -> !p.isDeleted())
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        for (Long id : set) {
            Project p = map.get(id);
            if (p == null) {
                throw new BizException("关联的项目不存在");
            }
            if (!p.involves(cu.id())) {
                throw new BizException("您不是项目「" + p.getName() + "」的成员，不能填报该项目");
            }
            if (!p.getStatus().fillable()) {
                throw new BizException("项目「" + p.getName() + "」已" + p.getStatus().label() + "，不能再填报");
            }
        }
        return map;
    }

    private WeeklyPlan manageable(CurrentUser cu, Long id) {
        WeeklyPlan p = planRepo.findWithItemsById(id).orElseThrow(() -> BizException.notFound("周报"));
        if (p.getUserId().equals(cu.id())) {
            throw new BizException("不能点评或退回自己的周报");
        }
        if (!canManage(cu, p, PERM_COMMENT)) {
            throw new BizException(HttpStatus.FORBIDDEN, "没有点评该周报的权限");
        }
        return p;
    }

    private boolean canManage(CurrentUser cu, WeeklyPlan p, String perm) {
        if (!(cu.superAdmin() || cu.permissions().contains(perm)) || cu.dataScope() == DataScope.SELF) {
            return false;
        }
        Long deptId = userRepo.findById(p.getUserId()).map(SysUser::getDeptId).orElse(null);
        return scope.canSeeDept(cu, deptId);
    }

    private static boolean shouldFill(SysUser u) {
        return u.getRoles().stream().anyMatch(r -> r.isSuperAdmin() || r.getPermissions().contains(PERM_FILL))
                && !u.getRoles().stream().allMatch(SysRole::isSuperAdmin);
    }

    private LocalDate normalize(LocalDate weekStart) {
        return weekStart == null ? rules.currentMonday() : WeekRules.mondayOf(weekStart);
    }

    private static Integer rate(WeeklyPlan p) {
        return (int) Math.round(p.getItems().stream().filter(i -> !i.isExtra() && i.getCompletion() != null)
                .mapToInt(WeeklyItem::getCompletion).average().orElse(0));
    }

    private static BigDecimal planDays(WeeklyPlan p) {
        return p.getItems().stream().filter(i -> !i.isExtra() && i.getPlanDays() != null).map(WeeklyItem::getPlanDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ 转换

    private PlanVO toVO(WeeklyPlan p) {
        SysUser u = userRepo.findById(p.getUserId()).orElse(null);
        String commentBy = p.getCommentBy() == null ? null
                : userRepo.findById(p.getCommentBy()).map(SysUser::getRealName).orElse(null);
        return new PlanVO(p.getId(), p.getUserId(), u == null ? null : u.getRealName(),
                u == null ? null : deptService.nameOf(u.getDeptId()), rules.info(p.getWeekStart()), p.getPlanStatus(),
                p.getPlanSubmittedAt(), p.isPlanOverdue(), p.getReportStatus(), p.getReportSubmittedAt(),
                p.isReportOverdue(), p.getCompletionRate(), planDays(p), p.getComment(), commentBy, p.getCommentAt(),
                p.getReturnReason(), toItems(p.getItems()));
    }

    private List<ItemVO> toItems(List<WeeklyItem> items) {
        Set<Long> ids = new HashSet<>();
        items.forEach(i -> {
            if (i.getProjectId() != null) {
                ids.add(i.getProjectId());
            }
        });
        Map<Long, Project> projects = ids.isEmpty() ? new HashMap<>()
                : projectRepo.findByIdIn(ids).stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        return items.stream().map(i -> {
            Project pj = i.getProjectId() == null ? null : projects.get(i.getProjectId());
            return new ItemVO(i.getId(), i.getProjectId(), pj == null ? null : pj.getCode(),
                    pj == null ? null : pj.getName(), i.getContent(), i.getTarget(), i.getPlanDays(), i.getRemark(),
                    i.isExtra(), i.getCompletion(), i.getActualContent(), i.getActualDays(), i.getReason());
        }).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
