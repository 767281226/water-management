package com.hongyu.water.system.log;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_oper_log")
public class SysOperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String username;

    @Column(name = "real_name")
    private String realName;

    private String module;
    private String action;
    private String method;
    private String uri;
    private String params;
    private boolean success;

    @Column(name = "error_msg")
    private String errorMsg;

    private String ip;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now().withNano(0);
}
