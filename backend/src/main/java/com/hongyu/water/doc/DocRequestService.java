package com.hongyu.water.doc;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.doc.DocDtos.ApplyRequest;
import com.hongyu.water.doc.DocDtos.RequestActions;
import com.hongyu.water.doc.DocDtos.RequestItemVO;
import com.hongyu.water.doc.DocDtos.RequestVO;
import com.hongyu.water.doc.DocDtos.StepVO;
import com.hongyu.water.project.Project;
import com.hongyu.water.project.ProjectRepository;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.DataScopeHelper;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;

/**
 * 资料下载申请与审批（需求 F-404 ~ F-408）。
 * <ul>
 * <li>审批流程：部门负责人审批；申请中含涉密资料时，再由公司领导终审；</li>
 * <li>部门负责人 = 拥有下载审批权限（doc:approve）且数据范围覆盖申请人部门；公司领导 = 下载审批权限 + 全公司数据范围；</li>
 * <li>不能审批自己的申请；审批通过后在有效期内可下载，每份资料限下载次数（参数配置 › 资料设置）。</li>
 * </ul>
 */
@Service
public class DocRequestService {

    static final String PERM_APPROVE = "doc:approve";

    private final DocRequestRepository repo;
    private final DocRepository docRepo;
    private final ProjectRepository projectRepo;
    private final SysUserRepository userRepo;
    private final DeptService deptService;
    private final DataScopeHelper scope;
    private final ConfigService configService;

    public DocRequestService(DocRequestRepository repo, DocRepository docRepo, ProjectRepository projectRepo,
            SysUserRepository userRepo, DeptService deptService, DataScopeHelper scope, ConfigService configService) {
        this.repo = repo;
        this.docRepo = docRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
        this.deptService = deptService;
        this.scope = scope;
        this.configService = configService;
    }

    // ------------------------------------------------------------------ 申请

    @Transactional
    public RequestVO apply(ApplyRequest req, CurrentUser cu) {
        Set<Long> ids = new LinkedHashSet<>(req.docIds());
        Map<Long, Doc> docs = docRepo.findByIdIn(ids).stream().filter(Doc::available)
                .collect(Collectors.toMap(Doc::getId, Function.identity()));
        if (docs.size() != ids.size()) {
            throw new BizException("部分资料不存在或已下架，请刷新后重试");
        }
        List<Doc> needApproval = ids.stream().map(docs::get).filter(Doc::isNeedApproval).toList();
        if (needApproval.isEmpty()) {
            throw new BizException("所选资料无需审批，可以直接下载");
        }
        // 防止重复申请
        LocalDateTime now = LocalDateTime.now();
        for (DocRequest r : repo.findByApplicantIdAndStatusIn(cu.id(), List.of(DocRequest.Status.PENDING, DocRequest.Status.APPROVED))) {
            for (DocRequest.Item it : r.getItems()) {
                Doc d = docs.get(it.getDocId());
                if (d == null) {
                    continue;
                }
                if (r.getStatus() == DocRequest.Status.PENDING) {
                    throw new BizException("「" + d.getName() + "」已在审批中，请勿重复申请");
                }
                if (r.valid(now) && it.getDownloadCount() < r.getMaxDownloads()) {
                    throw new BizException("「" + d.getName() + "」已审批通过，可以直接下载");
                }
            }
        }
        if (req.projectId() != null) {
            Project p = projectRepo.findByIdAndDeletedFalse(req.projectId()).orElseThrow(() -> BizException.notFound("项目"));
            if (!p.involves(cu.id())) {
                throw new BizException("只能关联自己参与的项目");
            }
        }
        DocRequest r = new DocRequest();
        r.setApplicantId(cu.id());
        r.setProjectId(req.projectId());
        r.setPurpose(req.purpose().trim());
        r.setMaxDownloads(configService.getInt("doc.download.max-times", 3));
        needApproval.forEach(d -> r.getItems().add(new DocRequest.Item(d.getId())));
        addStep(r, 1, "部门负责人审批", DocRequestStep.ApproverType.DEPT, DocRequestStep.Status.PENDING);
        if (needApproval.stream().anyMatch(d -> d.getLevel() == Doc.Level.SECRET)) {
            addStep(r, 2, "公司领导终审", DocRequestStep.ApproverType.LEADER, DocRequestStep.Status.WAITING);
        }
        repo.save(r);
        return toVO(r, cu);
    }

    @Transactional
    public void cancel(Long id, CurrentUser cu) {
        DocRequest r = mustGet(id);
        if (!r.getApplicantId().equals(cu.id())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能撤回自己的申请");
        }
        if (r.getStatus() != DocRequest.Status.PENDING) {
            throw new BizException("只能撤回审批中的申请");
        }
        r.setStatus(DocRequest.Status.CANCELLED);
        r.setFinishedAt(LocalDateTime.now().withNano(0));
        r.getSteps().stream().filter(s -> s.getStatus() == DocRequestStep.Status.PENDING || s.getStatus() == DocRequestStep.Status.WAITING)
                .forEach(s -> s.setStatus(DocRequestStep.Status.CANCELLED));
    }

    @Transactional(readOnly = true)
    public PageResult<RequestVO> mine(CurrentUser cu, Integer page, Integer size) {
        var p = repo.findByApplicantIdOrderByIdDesc(cu.id(), PageQuery.of(page, size, Sort.unsorted()));
        return PageResult.of(p, r -> toVO(r, cu));
    }

    // ------------------------------------------------------------------ 审批

    @Transactional(readOnly = true)
    public List<RequestVO> todo(CurrentUser cu) {
        return repo.findByStatusOrderByIdAsc(DocRequest.Status.PENDING).stream().filter(r -> canApprove(r, cu))
                .map(r -> toVO(r, cu)).toList();
    }

    @Transactional(readOnly = true)
    public int todoCount(CurrentUser cu) {
        if (!has(cu, PERM_APPROVE)) {
            return 0;
        }
        return (int) repo.findByStatusOrderByIdAsc(DocRequest.Status.PENDING).stream().filter(r -> canApprove(r, cu)).count();
    }

    @Transactional(readOnly = true)
    public PageResult<RequestVO> done(CurrentUser cu, Integer page, Integer size) {
        var p = repo.findApprovedBy(cu.id(), PageQuery.of(page, size, Sort.unsorted()));
        return PageResult.of(p, r -> toVO(r, cu));
    }

    @Transactional
    public void approve(Long id, String comment, CurrentUser cu) {
        DocRequest r = mustGet(id);
        DocRequestStep step = checkApprovable(r, cu);
        step.setStatus(DocRequestStep.Status.APPROVED);
        step.setApproverId(cu.id());
        step.setComment(comment == null || comment.isBlank() ? "同意" : comment.trim());
        step.setActedAt(LocalDateTime.now().withNano(0));
        r.getSteps().stream().filter(s -> s.getStatus() == DocRequestStep.Status.WAITING).findFirst()
                .ifPresentOrElse(next -> next.setStatus(DocRequestStep.Status.PENDING), () -> {
                    LocalDateTime now = LocalDateTime.now().withNano(0);
                    r.setStatus(DocRequest.Status.APPROVED);
                    r.setFinishedAt(now);
                    r.setExpireAt(now.plusDays(configService.getInt("doc.download.valid-days", 7)));
                });
    }

    @Transactional
    public void reject(Long id, String comment, CurrentUser cu) {
        DocRequest r = mustGet(id);
        DocRequestStep step = checkApprovable(r, cu);
        step.setStatus(DocRequestStep.Status.REJECTED);
        step.setApproverId(cu.id());
        step.setComment(comment.trim());
        step.setActedAt(LocalDateTime.now().withNano(0));
        r.getSteps().stream().filter(s -> s.getStatus() == DocRequestStep.Status.WAITING)
                .forEach(s -> s.setStatus(DocRequestStep.Status.CANCELLED));
        r.setStatus(DocRequest.Status.REJECTED);
        r.setFinishedAt(LocalDateTime.now().withNano(0));
    }

    /** 批量通过，返回成功数量（跳过无权审批的） */
    @Transactional
    public int batchApprove(List<Long> ids, CurrentUser cu) {
        int n = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            DocRequest r = repo.findWithStepsById(id).orElse(null);
            if (r != null && canApprove(r, cu)) {
                approve(id, "同意", cu);
                n++;
            }
        }
        if (n == 0) {
            throw new BizException("所选申请都不能由您审批");
        }
        return n;
    }

    // ------------------------------------------------------------------ 下载

    /**
     * 校验下载权限，返回用于计数的申请（免审资料返回 null）。
     */
    @Transactional(readOnly = true)
    public DocRequest checkDownload(Doc d, Long requestId, Long userId) {
        if (!d.isNeedApproval()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        List<DocRequest> candidates = requestId != null
                ? repo.findById(requestId).filter(r -> r.getApplicantId().equals(userId)).stream().toList()
                : repo.findByApplicantIdAndStatusIn(userId, List.of(DocRequest.Status.APPROVED));
        for (DocRequest r : candidates) {
            if (!r.valid(now)) {
                continue;
            }
            for (DocRequest.Item it : r.getItems()) {
                if (it.getDocId().equals(d.getId()) && it.getDownloadCount() < r.getMaxDownloads()) {
                    return r;
                }
            }
        }
        throw new BizException(HttpStatus.FORBIDDEN, "该资料需要审批后才能下载，或下载次数 / 有效期已用完");
    }

    @Transactional
    public void recordDownload(Long requestId, Long docId) {
        DocRequest r = mustGet(requestId);
        r.getItems().stream().filter(it -> it.getDocId().equals(docId)).findFirst()
                .ifPresent(it -> it.setDownloadCount(it.getDownloadCount() + 1));
    }

    // ------------------------------------------------------------------ 内部

    private DocRequestStep checkApprovable(DocRequest r, CurrentUser cu) {
        if (r.getStatus() != DocRequest.Status.PENDING) {
            throw new BizException("该申请已处理");
        }
        DocRequestStep step = r.currentStep().orElseThrow(() -> new BizException("该申请没有待审批的步骤"));
        if (r.getApplicantId().equals(cu.id())) {
            throw new BizException("不能审批自己的申请");
        }
        if (!canApproveStep(r, step, cu)) {
            throw new BizException(HttpStatus.FORBIDDEN, step.getApproverType() == DocRequestStep.ApproverType.LEADER
                    ? "涉密资料需要公司领导终审" : "没有审批该申请的权限");
        }
        return step;
    }

    private boolean canApprove(DocRequest r, CurrentUser cu) {
        return r.getStatus() == DocRequest.Status.PENDING && !r.getApplicantId().equals(cu.id())
                && r.currentStep().map(s -> canApproveStep(r, s, cu)).orElse(false);
    }

    private boolean canApproveStep(DocRequest r, DocRequestStep step, CurrentUser cu) {
        if (!has(cu, PERM_APPROVE)) {
            return false;
        }
        if (step.getApproverType() == DocRequestStep.ApproverType.LEADER) {
            return cu.dataScope() == DataScope.ALL;
        }
        if (cu.dataScope() == DataScope.SELF) {
            return false;
        }
        Long deptId = userRepo.findById(r.getApplicantId()).map(SysUser::getDeptId).orElse(null);
        return scope.canSeeDept(cu, deptId);
    }

    private static void addStep(DocRequest r, int no, String name, DocRequestStep.ApproverType type, DocRequestStep.Status status) {
        DocRequestStep s = new DocRequestStep();
        s.setRequest(r);
        s.setStepNo(no);
        s.setName(name);
        s.setApproverType(type);
        s.setStatus(status);
        r.getSteps().add(s);
    }

    private DocRequest mustGet(Long id) {
        return repo.findWithStepsById(id).orElseThrow(() -> BizException.notFound("申请"));
    }

    private RequestVO toVO(DocRequest r, CurrentUser cu) {
        Set<Long> uids = new HashSet<>();
        uids.add(r.getApplicantId());
        r.getSteps().stream().map(DocRequestStep::getApproverId).filter(Objects::nonNull).forEach(uids::add);
        Map<Long, SysUser> users = userRepo.findByIdIn(uids).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Doc> docs = docRepo.findByIdIn(r.getItems().stream().map(DocRequest.Item::getDocId).toList()).stream()
                .collect(Collectors.toMap(Doc::getId, Function.identity()));
        SysUser applicant = users.get(r.getApplicantId());
        String project = r.getProjectId() == null ? null : projectRepo.findById(r.getProjectId()).map(Project::getName).orElse(null);
        List<RequestItemVO> items = r.getItems().stream().map(it -> {
            Doc d = docs.get(it.getDocId());
            return d == null ? new RequestItemVO(it.getDocId(), "（已删除）", null, null, null, 0, it.getDownloadCount(), false)
                    : new RequestItemVO(d.getId(), d.getName(), d.getCode(), d.getLevel(), d.getFileExt(), d.getFileSize(),
                            it.getDownloadCount(), d.available());
        }).toList();
        List<StepVO> steps = r.getSteps().stream().map(s -> new StepVO(s.getStepNo(), s.getName(), s.getApproverType(), s.getStatus(),
                s.getApproverId() == null ? null : users.get(s.getApproverId()).getRealName(), s.getComment(), s.getActedAt())).toList();
        boolean mine = r.getApplicantId().equals(cu.id());
        boolean secret = items.stream().anyMatch(i -> i.level() == Doc.Level.SECRET);
        return new RequestVO(r.getId(), r.getApplicantId(), applicant == null ? null : applicant.getRealName(),
                applicant == null ? null : deptService.nameOf(applicant.getDeptId()), project, r.getPurpose(), r.getStatus(),
                r.getCreatedAt(), r.getExpireAt(), r.getMaxDownloads(), secret, items, steps,
                new RequestActions(canApprove(r, cu), mine && r.getStatus() == DocRequest.Status.PENDING,
                        mine && r.valid(LocalDateTime.now())));
    }

    static boolean has(CurrentUser cu, String perm) {
        return cu.superAdmin() || cu.permissions().contains(perm);
    }
}
