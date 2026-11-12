package com.hongyu.water.system.config;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统参数（键值对），参数项由 Flyway 脚本定义，界面上只能修改值。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_config")
public class SysConfig {

    @Id
    @Column(name = "config_key")
    private String configKey;

    @Column(name = "config_value")
    private String configValue;

    @Column(name = "group_code")
    private String groupCode;

    private String name;
    private String remark;
    private int sort;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now().withNano(0);
    }
}
