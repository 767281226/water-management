package com.hongyu.water.system.auth;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.system.role.DataScope;
import com.hongyu.water.system.role.SysPermission;
import com.hongyu.water.system.role.SysPermissionRepository;
import com.hongyu.water.system.role.SysRole;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.system.user.UserStatus;

/**
 * 加载当前用户的状态、权限与数据范围。超级管理员自动拥有全部权限点（含后续新增的）。
 */
@Service
public class CurrentUserLoader {

    private final SysUserRepository userRepo;
    private final SysPermissionRepository permRepo;

    public CurrentUserLoader(SysUserRepository userRepo, SysPermissionRepository permRepo) {
        this.userRepo = userRepo;
        this.permRepo = permRepo;
    }

    /** 令牌校验：用户存在、已启用且令牌版本一致才有效。 */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> load(long userId, int tokenVersion) {
        return userRepo.findWithRolesByIdAndDeletedFalse(userId)
                .filter(u -> u.getStatus() == UserStatus.ENABLED && u.getTokenVersion() == tokenVersion)
                .map(this::toCurrentUser);
    }

    @Transactional(readOnly = true)
    public CurrentUser toCurrentUser(SysUser u) {
        boolean superAdmin = u.isSuperAdmin();
        Set<String> perms = superAdmin
                ? permRepo.findAll().stream().map(SysPermission::getCode).collect(Collectors.toSet())
                : u.getRoles().stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet());
        DataScope scope = superAdmin ? DataScope.ALL
                : u.getRoles().stream().map(SysRole::getDataScope).max(Comparator.naturalOrder()).orElse(DataScope.SELF);
        return new CurrentUser(u.getId(), u.getUsername(), u.getRealName(), u.getDeptId(), superAdmin, scope,
                Set.copyOf(perms), u.isMustChangePwd());
    }
}
