package com.hongyu.water.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.project.ProjectDtos.ProjectActions;
import com.hongyu.water.project.ProjectDtos.ProjectDetail;
import com.hongyu.water.project.ProjectDtos.ProjectForm;
import com.hongyu.water.project.ProjectDtos.ProjectOption;
import com.hongyu.water.project.ProjectDtos.ProjectQuery;
import com.hongyu.water.project.ProjectDtos.ProjectVO;
import com.hongyu.water.project.ProjectDtos.ProjectWeeklyEntry;
import com.hongyu.water.project.ProjectDtos.UserRef;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.DataScopeHelper;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.system.user.UserStatus;
import com.hongyu.water.weekly.WeeklyItem;
import com.hongyu.water.weekly.WeeklyItemRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * 项目台账。
 * <ul>
 * <li>可见范围：自己负责 / 参与的项目 + 数据范围内部门的项目；</li>
 * <li>编辑：项目负责人，或拥有"新建 / 编辑项目"权限且项目在数据范围内；</li>
 * <li>存档：项目负责人申请 → 拥有"确认存档"权限的部门负责人 / 领导确认（需求 F-303）。</li>
 * </ul>
 */
@Service
public class ProjectService {

    static final String PERM_EDIT = "project:ledger:edit";
    static final String PERM_ARCHIVE = "project:ledger:archive";

    private final ProjectRepository repo;
    private final WeeklyItemRepository itemRepo;
    private final SysUserRepository userRepo;
    private final DeptService deptService;
    private final DataScopeHelper scope;
    private final ConfigService configService;

    public ProjectService(ProjectRepository repo, WeeklyItemRepository itemRepo, SysUserRepository userRepo,
            DeptService deptService, DataScopeHelper scope, ConfigService configService) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.userRepo = userRepo;
        this.deptService = deptService;
        this.scope = scope;
        this.configService = configService;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResult<ProjectVO> page(ProjectQuery q, CurrentUser cu) {
        Set<Long> visibleDepts = scope.visibleDeptIds(cu);
        Set<Long> filterDepts = q.deptId() == null ? null : deptService.selfAndDescendants(q.deptId());
        boolean onlyMine = Boolean.TRUE.equals(q.mine());
        Specification<Project> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            Predicate involved = cb.or(cb.equal(root.get("leaderId"), cu.id()), cb.isMember(cu.id(), root.get("memberIds")));
            if (onlyMine) {
                ps.add(involved);
            } else if (visibleDepts != null) {
                ps.add(visibleDepts.isEmpty() ? involved : cb.or(involved, root.get("deptId").in(visibleDepts)));
            }
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String like = "%" + q.keyword().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("client")), like)));
            }
            if (q.status() != null) {
                ps.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.type() != null && !q.type().isBlank()) {
                ps.add(cb.equal(root.get("type"), q.type()));
            }
            if (filterDepts != null) {
                ps.add(root.get("deptId").in(filterDepts));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var page = repo.findAll(spec, PageQuery.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> names = userNames(page.getContent().stream().flatMap(this::userIdsOf).collect(Collectors.toSet()));
        Map<Long, String> deptNames = deptService.nameMap();
        return PageResult.of(page, p -> toVO(p, cu, names, deptNames));
    }

    @Transactional(readOnly = true)
    public ProjectDetail detail(Long id, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        List<WeeklyItem> items = itemRepo.findRecentByProject(id, PageRequest.of(0, 60));
        Set<Long> ids = new HashSet<>(userIdsOf(p).toList());
        items.forEach(i -> ids.add(i.getPlan().getUserId()));
        Map<Long, String> names = userNames(ids);
        List<ProjectWeeklyEntry> entries = items.stream().map(i -> new ProjectWeeklyEntry(i.getId(),
                i.getPlan().getWeekStart(), i.getPlan().getUserId(), names.get(i.getPlan().getUserId()), i.getContent(),
                i.getTarget(), i.getPlanDays(), i.getCompletion(), i.getActualContent(), i.getActualDays(), i.getReason(),
                i.isExtra(), i.getPlan().reported())).toList();
        BigDecimal days = itemRepo.sumDaysByProject(id);
        return new ProjectDetail(toVO(p, cu, names, deptService.nameMap()), itemRepo.countByProjectId(id), days,
                p.getMemberIds().size() + 1, entries);
    }

    /** 周报中可选择的项目：我负责 / 参与且未存档、未终止。 */
    @Transactional(readOnly = true)
    public List<ProjectOption> fillableOptions(CurrentUser cu) {
        return repo.findInvolving(cu.id()).stream().filter(p -> p.getStatus().fillable())
                .map(p -> new ProjectOption(p.getId(), p.getCode(), p.getName(), p.getStatus())).toList();
    }

    public List<String> types() {
        return Arrays.stream(configService.get("project.types", "规划,设计,咨询").split("[,，]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // ------------------------------------------------------------------ 新建 / 编辑

    @Transactional
    public Long create(ProjectForm form, CurrentUser cu) {
        Project p = new Project();
        p.setCreatedBy(cu.id());
        String code = form.code() == null || form.code().isBlank() ? nextCode() : form.code().trim();
        apply(p, form, code, cu);
        return repo.save(p).getId();
    }

    @Transactional
    public void update(Long id, ProjectForm form, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!actions(p, cu).edit()) {
            throw forbidden(p.getStatus() == ProjectStatus.ARCHIVED ? "已存档的项目不能修改" : "只有项目负责人或有编辑权限的人员才能修改项目");
        }
        String code = form.code() == null || form.code().isBlank() ? p.getCode() : form.code().trim();
        apply(p, form, code, cu);
    }

    @Transactional
    public void changeStatus(Long id, ProjectStatus target, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!actions(p, cu).changeStatus()) {
            throw forbidden("只有项目负责人或有编辑权限的人员才能修改项目状态");
        }
        if (!p.getStatus().canChangeTo(target)) {
            throw new BizException("项目当前为「" + p.getStatus().label() + "」，不能改为「" + target.label() + "」");
        }
        LocalDate today = LocalDate.now();
        if (target == ProjectStatus.IN_PROGRESS && p.getStartDate() == null) {
            p.setStartDate(today);
        }
        if (target == ProjectStatus.COMPLETED) {
            p.setActualEndDate(today);
        }
        if (p.getStatus() == ProjectStatus.COMPLETED && target == ProjectStatus.IN_PROGRESS) {
            p.setActualEndDate(null);
            p.setArchiveApplied(false);
        }
        p.setStatus(target);
    }

    // ------------------------------------------------------------------ 存档

    @Transactional
    public void applyArchive(Long id, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!cu.id().equals(p.getLeaderId())) {
            throw forbidden("只有项目负责人才能申请存档");
        }
        if (p.getStatus() != ProjectStatus.COMPLETED) {
            throw new BizException("项目完成后才能申请存档");
        }
        if (p.isArchiveApplied()) {
            throw new BizException("已经申请过存档，请等待确认");
        }
        p.setArchiveApplied(true);
        p.setArchiveRejectReason(null);
    }

    @Transactional
    public void confirmArchive(Long id, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!actions(p, cu).confirmArchive()) {
            throw forbidden("没有确认该项目存档的权限，或项目尚未申请存档");
        }
        p.setStatus(ProjectStatus.ARCHIVED);
        p.setArchiveApplied(false);
        p.setArchivedAt(LocalDateTime.now().withNano(0));
        p.setArchivedBy(cu.id());
    }

    @Transactional
    public void rejectArchive(Long id, String reason, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!actions(p, cu).confirmArchive()) {
            throw forbidden("没有审核该项目存档的权限，或项目尚未申请存档");
        }
        p.setArchiveApplied(false);
        p.setArchiveRejectReason(reason.trim());
    }

    @Transactional
    public void delete(Long id, CurrentUser cu) {
        Project p = mustGetVisible(id, cu);
        if (!actions(p, cu).delete()) {
            throw forbidden("没有删除该项目的权限");
        }
        long refs = itemRepo.countByProjectId(id);
        if (refs > 0) {
            throw new BizException("项目已有 " + refs + " 条周报记录，不能删除；如项目取消请改为「终止」");
        }
        p.setDeleted(true);
    }

    // ------------------------------------------------------------------ 内部

    /** 当前用户对项目可执行的操作。 */
    ProjectActions actions(Project p, CurrentUser cu) {
        boolean leader = cu.id().equals(p.getLeaderId());
        boolean inScope = scope.canSeeDept(cu, p.getDeptId());
        boolean editor = has(cu, PERM_EDIT) && inScope;
        boolean archived = p.getStatus() == ProjectStatus.ARCHIVED;
        boolean edit = !archived && (leader || editor);
        boolean apply = leader && p.getStatus() == ProjectStatus.COMPLETED && !p.isArchiveApplied();
        boolean confirm = has(cu, PERM_ARCHIVE) && inScope && cu.dataScope() != DataScope.SELF
                && p.getStatus() == ProjectStatus.COMPLETED && p.isArchiveApplied();
        boolean delete = editor && !archived;
        return new ProjectActions(edit, edit, apply, confirm, delete);
    }

    private void apply(Project p, ProjectForm f, String code, CurrentUser cu) {
        if (repo.codeTaken(code, p.getId() == null ? 0L : p.getId())) {
            throw new BizException("项目编号「" + code + "」已存在");
        }
        if (!types().contains(f.type())) {
            throw new BizException("项目类型不正确，可选：" + String.join("、", types()));
        }
        if (f.startDate() != null && f.planEndDate().isBefore(f.startDate())) {
            throw new BizException("计划完成日期不能早于开始日期");
        }
        SysUser leader = activeUser(f.leaderId(), "项目负责人");
        Long deptId = f.deptId() != null ? f.deptId() : leader.getDeptId();
        if (deptId != null) {
            deptService.mustGet(deptId);
        }
        // 非全公司范围的编辑人员，只能把项目放在自己数据范围内的部门（项目负责人修改自己的项目除外）
        boolean leaderSelf = cu.id().equals(p.getLeaderId());
        if (!leaderSelf && !scope.canSeeDept(cu, deptId)) {
            throw forbidden("只能在自己负责的部门下创建或调整项目");
        }
        Set<Long> members = new HashSet<>(f.memberIds() == null ? Set.of() : f.memberIds());
        members.remove(f.leaderId());
        for (Long m : members) {
            activeUser(m, "参与人员");
        }
        p.setCode(code);
        p.setName(f.name().trim());
        p.setType(f.type());
        p.setClient(blankToNull(f.client()));
        p.setLeaderId(f.leaderId());
        p.setDeptId(deptId);
        p.setContractAmount(f.contractAmount());
        p.setStartDate(f.startDate());
        p.setPlanEndDate(f.planEndDate());
        p.setMemberIds(members);
        p.setRemark(blankToNull(f.remark()));
    }

    /** 自动编号：前缀-年份-三位序号，如 HY-2026-001 */
    private String nextCode() {
        String prefix = configService.get("project.code-prefix", "HY").trim() + "-" + LocalDate.now().getYear() + "-";
        int max = repo.findCodesLike(prefix + "%").stream().map(c -> c.substring(prefix.length()))
                .filter(s -> s.matches("\\d+")).mapToInt(Integer::parseInt).max().orElse(0);
        return prefix + String.format("%03d", max + 1);
    }

    private SysUser activeUser(Long id, String what) {
        SysUser u = userRepo.findWithRolesByIdAndDeletedFalse(id).orElseThrow(() -> new BizException(what + "不存在"));
        if (u.getStatus() != UserStatus.ENABLED) {
            throw new BizException(what + "「" + u.getRealName() + "」已停用");
        }
        return u;
    }

    Project mustGetVisible(Long id, CurrentUser cu) {
        Project p = repo.findByIdAndDeletedFalse(id).orElseThrow(() -> BizException.notFound("项目"));
        if (!p.involves(cu.id()) && !scope.canSeeDept(cu, p.getDeptId())) {
            throw forbidden("没有查看该项目的权限");
        }
        return p;
    }

    private ProjectVO toVO(Project p, CurrentUser cu, Map<Long, String> names, Map<Long, String> deptNames) {
        boolean leader = cu.id().equals(p.getLeaderId());
        // 合同金额仅对项目负责人、部门负责人及以上可见（需求 C4）
        BigDecimal amount = cu.dataScope() != DataScope.SELF || leader ? p.getContractAmount() : null;
        List<UserRef> members = p.getMemberIds().stream().sorted().map(m -> new UserRef(m, names.get(m))).toList();
        return new ProjectVO(p.getId(), p.getCode(), p.getName(), p.getType(), p.getClient(), p.getLeaderId(),
                names.get(p.getLeaderId()), p.getDeptId(), deptNames.get(p.getDeptId()), amount, p.getStartDate(),
                p.getPlanEndDate(), p.getActualEndDate(), p.getStatus(), p.getStatus().label(), p.isArchiveApplied(),
                p.getArchiveRejectReason(), p.getArchivedAt(), names.get(p.getArchivedBy()), members, p.getRemark(),
                p.getCreatedAt(), actions(p, cu));
    }

    private Stream<Long> userIdsOf(Project p) {
        return Stream.concat(Stream.of(p.getLeaderId(), p.getArchivedBy()), p.getMemberIds().stream()).filter(Objects::nonNull);
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        return ids.isEmpty() ? Map.of()
                : userRepo.findByIdIn(ids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
    }

    static boolean has(CurrentUser cu, String perm) {
        return cu.superAdmin() || cu.permissions().contains(perm);
    }

    private static BizException forbidden(String msg) {
        return new BizException(HttpStatus.FORBIDDEN, msg);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
