package com.hongyu.water.system.user;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import com.hongyu.water.common.BaseEntity;
import com.hongyu.water.system.role.SysRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class SysUser extends BaseEntity {

    /** 登录账号 */
    private String username;

    /** 工号 */
    @Column(name = "employee_no")
    private String employeeNo;

    @Column(name = "real_name")
    private String realName;

    private String password;

    @Column(name = "dept_id")
    private Long deptId;

    /** 职务 */
    private String title;
    private String phone;
    private String email;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ENABLED;

    /** 内置账号，不可删除、停用 */
    private boolean builtin;

    @Column(name = "must_change_pwd")
    private boolean mustChangePwd = true;

    @Column(name = "token_version")
    private int tokenVersion;

    @Column(name = "login_fail_count")
    private int loginFailCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip")
    private String lastLoginIp;

    private String remark;

    private boolean deleted;

    @ManyToMany
    @JoinTable(name = "sys_user_role", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SysRole> roles = new HashSet<>();

    /** 使已签发的令牌全部失效 */
    public void revokeTokens() {
        tokenVersion++;
    }

    public boolean isSuperAdmin() {
        return roles.stream().anyMatch(SysRole::isSuperAdmin);
    }
}
