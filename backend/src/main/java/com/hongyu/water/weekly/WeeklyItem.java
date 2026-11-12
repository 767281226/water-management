package com.hongyu.water.weekly;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 周报事项。extra = true 为周五新增的计划外工作。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_weekly_item")
public class WeeklyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private WeeklyPlan plan;

    /** 关联项目；null 表示其他工作 */
    @Column(name = "project_id")
    private Long projectId;

    private String content;
    private String target;

    @Column(name = "plan_days")
    private BigDecimal planDays;

    private String remark;
    private boolean extra;

    /** 完成比例 0~100 */
    private Integer completion;

    @Column(name = "actual_content")
    private String actualContent;

    @Column(name = "actual_days")
    private BigDecimal actualDays;

    private String reason;
    private int sort;
}
