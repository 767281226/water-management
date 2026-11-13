package com.hongyu.water.doc;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 资料访问日志（需求 F-412）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_doc_log")
public class DocLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "user_id")
    private Long userId;

    /** VIEW / DOWNLOAD / UPLOAD / UPDATE / REPLACE / OFFLINE / ONLINE / DELETE / SYNC */
    private String action;

    @Column(name = "request_id")
    private Long requestId;

    private String ip;
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now().withNano(0);
}
