package com.hongyu.water.system.log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.common.WebUtils;

import jakarta.persistence.criteria.Predicate;

/**
 * 操作日志、登录日志。写日志使用独立事务，业务失败回滚时日志仍然保留。
 */
@Service
public class LogService {

    private final SysOperLogRepository operRepo;
    private final SysLoginLogRepository loginRepo;

    public LogService(SysOperLogRepository operRepo, SysLoginLogRepository loginRepo) {
        this.operRepo = operRepo;
        this.loginRepo = loginRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOper(SysOperLog log) {
        operRepo.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLogin(String username, boolean success, String message, String ip, String userAgent) {
        SysLoginLog l = new SysLoginLog();
        l.setUsername(WebUtils.truncate(username, 64));
        l.setSuccess(success);
        l.setMessage(WebUtils.truncate(message, 255));
        l.setIp(ip);
        l.setUserAgent(userAgent);
        loginRepo.save(l);
    }

    public record LogQuery(String keyword, String module, Boolean success, LocalDate start, LocalDate end,
            Integer page, Integer size) {
    }

    @Transactional(readOnly = true)
    public PageResult<SysOperLog> pageOper(LogQuery q) {
        Specification<SysOperLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (hasText(q.keyword())) {
                String like = "%" + q.keyword().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), like), cb.like(root.get("realName"), like),
                        cb.like(cb.lower(root.get("action")), like), cb.like(cb.lower(root.get("params")), like)));
            }
            if (hasText(q.module())) {
                ps.add(cb.equal(root.get("module"), q.module()));
            }
            if (q.success() != null) {
                ps.add(cb.equal(root.get("success"), q.success()));
            }
            if (q.start() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.start().atStartOfDay()));
            }
            if (q.end() != null) {
                ps.add(cb.lessThan(root.get("createdAt"), q.end().plusDays(1).atStartOfDay()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResult.of(operRepo.findAll(spec, PageQuery.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "id"))),
                l -> l);
    }

    @Transactional(readOnly = true)
    public PageResult<SysLoginLog> pageLogin(LogQuery q) {
        Specification<SysLoginLog> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (hasText(q.keyword())) {
                String like = "%" + q.keyword().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), like), cb.like(root.get("ip"), like)));
            }
            if (q.success() != null) {
                ps.add(cb.equal(root.get("success"), q.success()));
            }
            if (q.start() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.start().atStartOfDay()));
            }
            if (q.end() != null) {
                ps.add(cb.lessThan(root.get("createdAt"), q.end().plusDays(1).atStartOfDay()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResult.of(loginRepo.findAll(spec, PageQuery.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "id"))),
                l -> l);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
