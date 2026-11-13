package com.hongyu.water.doc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.common.WebUtils;
import com.hongyu.water.doc.DocDtos.LogQuery;
import com.hongyu.water.doc.DocDtos.LogVO;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * 资料访问日志：预览、下载、上传、修改、上下架、删除、网盘同步。
 */
@Service
public class DocLogService {

    private final DocLogRepository repo;
    private final DocRepository docRepo;
    private final SysUserRepository userRepo;

    public DocLogService(DocLogRepository repo, DocRepository docRepo, SysUserRepository userRepo) {
        this.repo = repo;
        this.docRepo = docRepo;
        this.userRepo = userRepo;
    }

    /** 与业务操作同一事务（新上传的资料在同一事务中才可见，满足外键约束） */
    @Transactional
    public void log(Long docId, Long userId, String action, Long requestId, String detail) {
        DocLog l = new DocLog();
        l.setDocId(docId);
        l.setUserId(userId);
        l.setAction(action);
        l.setRequestId(requestId);
        l.setIp(WebUtils.clientIp(WebUtils.currentRequest()));
        l.setDetail(WebUtils.truncate(detail, 255));
        repo.save(l);
    }

    @Transactional(readOnly = true)
    public PageResult<LogVO> page(LogQuery q) {
        Set<Long> docIds = null;
        Set<Long> userIds = null;
        if (q.keyword() != null && !q.keyword().isBlank()) {
            String kw = q.keyword().trim();
            docIds = docRepo.findAll((r, cq, cb) -> cb.like(cb.lower(r.get("name")), "%" + kw.toLowerCase() + "%"))
                    .stream().map(Doc::getId).collect(Collectors.toSet());
            userIds = userRepo.findAll((r, cq, cb) -> cb.or(cb.like(r.get("realName"), "%" + kw + "%"),
                    cb.like(cb.lower(r.get("username")), "%" + kw.toLowerCase() + "%"))).stream().map(SysUser::getId)
                    .collect(Collectors.toSet());
        }
        Set<Long> fDocs = docIds, fUsers = userIds;
        Specification<DocLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (fDocs != null) {
                Predicate byDoc = fDocs.isEmpty() ? cb.disjunction() : root.get("docId").in(fDocs);
                Predicate byUser = fUsers.isEmpty() ? cb.disjunction() : root.get("userId").in(fUsers);
                ps.add(cb.or(byDoc, byUser));
            }
            if (q.action() != null && !q.action().isBlank()) {
                ps.add(cb.equal(root.get("action"), q.action()));
            }
            if (q.docId() != null) {
                ps.add(cb.equal(root.get("docId"), q.docId()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var page = repo.findAll(spec, PageQuery.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "id")));
        Set<Long> uids = new HashSet<>(), dids = new HashSet<>();
        page.getContent().forEach(l -> {
            if (l.getUserId() != null) {
                uids.add(l.getUserId());
            }
            if (l.getDocId() != null) {
                dids.add(l.getDocId());
            }
        });
        Map<Long, String> users = uids.isEmpty() ? Map.of()
                : userRepo.findByIdIn(uids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        Map<Long, String> docs = dids.isEmpty() ? Map.of()
                : docRepo.findByIdIn(dids).stream().collect(Collectors.toMap(Doc::getId, Doc::getName));
        return PageResult.of(page, l -> new LogVO(l.getId(), l.getCreatedAt(), users.get(l.getUserId()), l.getAction(),
                Objects.requireNonNullElse(docs.get(l.getDocId()), l.getDocId() == null ? "—" : "（已删除）"), l.getDocId(),
                l.getRequestId(), l.getIp(), l.getDetail()));
    }
}
