package com.hongyu.water.system.user;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.role.SysRole;
import com.hongyu.water.system.role.SysRoleRepository;
import com.hongyu.water.system.user.UserDtos.ResetPasswordResult;
import com.hongyu.water.system.user.UserDtos.RoleBrief;
import com.hongyu.water.system.user.UserDtos.UserForm;
import com.hongyu.water.system.user.UserDtos.UserOption;
import com.hongyu.water.system.user.UserDtos.UserQuery;
import com.hongyu.water.system.user.UserDtos.UserVO;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

/**
 * 用户管理。
 * <ul>
 * <li>内置管理员不可删除、停用，且必须保留超级管理员角色；</li>
 * <li>只有超级管理员才能分配超级管理员角色、或修改超级管理员账号（防止越权）；</li>
 * <li>停用、重置密码、删除都会使该用户已签发的令牌立即失效。</li>
 * </ul>
 */
@Service
public class UserService {

    private final SysUserRepository userRepo;
    private final SysRoleRepository roleRepo;
    private final DeptService deptService;
    private final ConfigService configService;
    private final PasswordEncoder encoder;

    public UserService(SysUserRepository userRepo, SysRoleRepository roleRepo, DeptService deptService,
            ConfigService configService, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.deptService = deptService;
        this.configService = configService;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(UserQuery q) {
        Set<Long> deptIds = q.deptId() == null ? null : deptService.selfAndDescendants(q.deptId());
        Specification<SysUser> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String like = "%" + q.keyword().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("username")), like), cb.like(root.get("realName"), like),
                        cb.like(cb.lower(root.get("employeeNo")), like), cb.like(root.get("phone"), like)));
            }
            if (deptIds != null) {
                ps.add(root.get("deptId").in(deptIds));
            }
            if (q.status() != null) {
                ps.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.roleId() != null) {
                Join<SysUser, SysRole> roles = root.join("roles");
                ps.add(cb.equal(roles.get("id"), q.roleId()));
                cq.distinct(true);
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(Sort.Order.desc("builtin"), Sort.Order.asc("id"));
        Map<Long, String> deptNames = deptService.nameMap();
        return PageResult.of(userRepo.findAll(spec, PageQuery.of(q.page(), q.size(), sort)), u -> toVO(u, deptNames));
    }

    @Transactional(readOnly = true)
    public UserVO get(Long id) {
        return toVO(mustGet(id), deptService.nameMap());
    }

    /** 下拉选项：启用状态的用户。 */
    @Transactional(readOnly = true)
    public List<UserOption> options() {
        Map<Long, String> deptNames = deptService.nameMap();
        return userRepo.findByDeletedFalseAndStatusOrderByIdAsc(UserStatus.ENABLED).stream()
                .map(u -> new UserOption(u.getId(), u.getRealName(), u.getEmployeeNo(), u.getDeptId(), deptNames.get(u.getDeptId())))
                .toList();
    }

    @Transactional
    public Long create(UserForm form) {
        checkUnique(form, 0L);
        SysUser u = new SysUser();
        apply(u, form);
        u.setRoles(resolveRoles(form.roleIds()));
        u.setPassword(encoder.encode(configService.initPassword()));
        u.setMustChangePwd(true);
        return userRepo.save(u).getId();
    }

    @Transactional
    public void update(Long id, UserForm form) {
        SysUser u = mustGet(id);
        guardSuperAdminTarget(u);
        checkUnique(form, id);
        if (u.isBuiltin() && !u.getUsername().equalsIgnoreCase(form.username().trim())) {
            throw new BizException("内置管理员的登录账号不可修改");
        }
        Set<SysRole> roles = resolveRoles(form.roleIds());
        if (u.isBuiltin() && roles.stream().noneMatch(SysRole::isSuperAdmin)) {
            throw new BizException("内置管理员必须保留超级管理员角色");
        }
        apply(u, form);
        u.setRoles(roles);
    }

    @Transactional
    public void changeStatus(Long id, UserStatus status) {
        SysUser u = mustGet(id);
        guardSuperAdminTarget(u);
        if (status == UserStatus.DISABLED) {
            if (u.isBuiltin()) {
                throw new BizException("内置管理员不可停用");
            }
            if (Objects.equals(u.getId(), SecurityUtils.current().id())) {
                throw new BizException("不能停用自己的账号");
            }
            u.revokeTokens();
        }
        u.setStatus(status);
    }

    @Transactional
    public void unlock(Long id) {
        SysUser u = mustGet(id);
        u.setLockedUntil(null);
        u.setLoginFailCount(0);
    }

    @Transactional
    public ResetPasswordResult resetPassword(Long id) {
        SysUser u = mustGet(id);
        guardSuperAdminTarget(u);
        String pwd = configService.initPassword();
        u.setPassword(encoder.encode(pwd));
        u.setMustChangePwd(true);
        u.setLockedUntil(null);
        u.setLoginFailCount(0);
        u.revokeTokens();
        return new ResetPasswordResult(pwd);
    }

    @Transactional
    public void delete(Long id) {
        SysUser u = mustGet(id);
        guardSuperAdminTarget(u);
        if (u.isBuiltin()) {
            throw new BizException("内置管理员不可删除");
        }
        if (Objects.equals(u.getId(), SecurityUtils.current().id())) {
            throw new BizException("不能删除自己的账号");
        }
        u.setDeleted(true);
        u.revokeTokens();
        deptService.clearLeader(u.getId());
    }

    // ------------------------------------------------------------------

    private SysUser mustGet(Long id) {
        return userRepo.findWithRolesByIdAndDeletedFalse(id).orElseThrow(() -> BizException.notFound("用户"));
    }

    /** 非超级管理员不能操作超级管理员账号。 */
    private void guardSuperAdminTarget(SysUser target) {
        CurrentUser cu = SecurityUtils.current();
        if (target.isSuperAdmin() && !cu.superAdmin()) {
            throw new BizException(HttpStatus.FORBIDDEN, "只有超级管理员才能操作管理员账号");
        }
    }

    private Set<SysRole> resolveRoles(List<Long> roleIds) {
        Set<Long> ids = new HashSet<>(roleIds);
        List<SysRole> roles = roleRepo.findByIdIn(ids);
        if (roles.size() != ids.size()) {
            throw new BizException("所选角色不存在");
        }
        if (roles.stream().anyMatch(SysRole::isSuperAdmin) && !SecurityUtils.current().superAdmin()) {
            throw new BizException(HttpStatus.FORBIDDEN, "只有超级管理员才能分配超级管理员角色");
        }
        return new HashSet<>(roles);
    }

    private void checkUnique(UserForm form, long excludeId) {
        if (userRepo.usernameTaken(form.username().trim(), excludeId)) {
            throw new BizException("登录账号「" + form.username().trim() + "」已存在");
        }
        if (userRepo.employeeNoTaken(form.employeeNo().trim(), excludeId)) {
            throw new BizException("工号「" + form.employeeNo().trim() + "」已存在");
        }
    }

    private void apply(SysUser u, UserForm form) {
        if (form.deptId() != null) {
            deptService.mustGet(form.deptId());
        }
        u.setUsername(form.username().trim());
        u.setEmployeeNo(form.employeeNo().trim());
        u.setRealName(form.realName().trim());
        u.setDeptId(form.deptId());
        u.setTitle(blankToNull(form.title()));
        u.setPhone(blankToNull(form.phone()));
        u.setEmail(blankToNull(form.email()));
        u.setRemark(blankToNull(form.remark()));
    }

    private UserVO toVO(SysUser u, Map<Long, String> deptNames) {
        List<RoleBrief> roles = u.getRoles().stream().sorted(Comparator.comparingInt(SysRole::getSort))
                .map(r -> new RoleBrief(r.getId(), r.getCode(), r.getName())).toList();
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now());
        return new UserVO(u.getId(), u.getUsername(), u.getEmployeeNo(), u.getRealName(), u.getDeptId(),
                deptNames.get(u.getDeptId()), u.getTitle(), u.getPhone(), u.getEmail(), u.getStatus(), u.isBuiltin(),
                locked, roles, u.getLastLoginAt(), u.getLastLoginIp(), u.getCreatedAt(), u.getRemark());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
