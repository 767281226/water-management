package com.hongyu.water.system.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限点（菜单 / 按钮），只通过 Flyway 脚本维护。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission")
public class SysPermission {

    @Id
    private String code;

    @Column(name = "parent_code")
    private String parentCode;

    private String name;

    /** MENU / BUTTON */
    private String type;

    private int sort;
}
