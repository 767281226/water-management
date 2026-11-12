package com.hongyu.water.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@Table(name = "biz_project")
public class Project extends BaseEntity {

    private String code;
    private String name;
    private String type;
    private String client;

    @Column(name = "leader_id")
    private Long leaderId;

    @Column(name = "dept_id")
    private Long deptId;

    /** 合同金额（万元） */
    @Column(name = "contract_amount")
    private BigDecimal contractAmount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "plan_end_date")
    private LocalDate planEndDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status = ProjectStatus.NOT_STARTED;

    @Column(name = "archive_applied")
    private boolean archiveApplied;

    @Column(name = "archive_reject_reason")
    private String archiveRejectReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

    private String remark;
    private boolean deleted;

    @Column(name = "created_by")
    private Long createdBy;

    /** 参与人员（不含负责人） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "biz_project_member", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "user_id")
    private Set<Long> memberIds = new HashSet<>();

    /** 负责人或参与人员 */
    public boolean involves(Long userId) {
        return userId != null && (userId.equals(leaderId) || memberIds.contains(userId));
    }
}
