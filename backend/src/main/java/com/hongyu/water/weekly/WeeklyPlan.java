package com.hongyu.water.weekly;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 周报：每人每周一条。周一提交计划，周五对照计划填写完成情况。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_weekly_plan")
public class WeeklyPlan extends BaseEntity {

    public enum PlanStatus { SUBMITTED, RETURNED }

    public enum ReportStatus { NOT_SUBMITTED, SUBMITTED }

    @Column(name = "user_id")
    private Long userId;

    /** 所在周的周一 */
    @Column(name = "week_start")
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status")
    private PlanStatus planStatus = PlanStatus.SUBMITTED;

    @Column(name = "plan_submitted_at")
    private LocalDateTime planSubmittedAt;

    @Column(name = "plan_overdue")
    private boolean planOverdue;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status")
    private ReportStatus reportStatus = ReportStatus.NOT_SUBMITTED;

    @Column(name = "report_submitted_at")
    private LocalDateTime reportSubmittedAt;

    @Column(name = "report_overdue")
    private boolean reportOverdue;

    /** 本周计划完成率（%） */
    @Column(name = "completion_rate")
    private Integer completionRate;

    private String comment;

    @Column(name = "comment_by")
    private Long commentBy;

    @Column(name = "comment_at")
    private LocalDateTime commentAt;

    @Column(name = "return_reason")
    private String returnReason;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sort asc, id asc")
    private List<WeeklyItem> items = new ArrayList<>();

    public boolean reported() {
        return reportStatus == ReportStatus.SUBMITTED;
    }
}
