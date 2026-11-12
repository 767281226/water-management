package com.hongyu.water.security;

import java.util.Set;

import com.hongyu.water.system.role.DataScope;

/**
 * 当前登录用户（放在 SecurityContext 中）。
 *
 * @param superAdmin    是否超级管理员（拥有全部权限）
 * @param dataScope     多个角色中最大的数据范围
 * @param mustChangePwd 必须先修改初始密码，修改前只能访问 /api/auth/**
 */
public record CurrentUser(Long id, String username, String realName, Long deptId, boolean superAdmin,
        DataScope dataScope, Set<String> permissions, boolean mustChangePwd) {
}
