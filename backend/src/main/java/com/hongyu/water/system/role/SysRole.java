package com.hongyu.water.system.role;

import java.util.HashSet;
import java.util.Set;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_role")
public class SysRole extends BaseEntity {

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope")
    private DataScope dataScope = DataScope.SELF;

    /** 内置角色，不可删除、不可修改编码 */
    private boolean builtin;

    /** 超级管理员角色，自动拥有全部权限 */
    @Column(name = "super_admin")
    private boolean superAdmin;

    private int sort;
    private String remark;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sys_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "perm_code")
    private Set<String> permissions = new HashSet<>();
}
