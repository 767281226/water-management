package com.hongyu.water.doc;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批步骤：DEPT 部门负责人审批 → LEADER 公司领导终审（申请中含涉密资料时）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_doc_request_step")
public class DocRequestStep {

    public enum ApproverType { DEPT, LEADER }

    /** WAITING 等待上一步 / PENDING 待审批 / APPROVED / REJECTED / CANCELLED */
    public enum Status { WAITING, PENDING, APPROVED, REJECTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private DocRequest request;

    @Column(name = "step_no")
    private int stepNo;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type")
    private ApproverType approverType;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "approver_id")
    private Long approverId;

    private String comment;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;
}
