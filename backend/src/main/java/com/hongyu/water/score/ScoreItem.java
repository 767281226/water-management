package com.hongyu.water.score;

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
 * 指标得分（保存打分时的指标名称和权重快照）。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_score_item")
public class ScoreItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_id")
    private Score score;

    @Column(name = "rule_key")
    private String ruleKey;

    @Column(name = "rule_name")
    private String ruleName;

    private int weight;

    /** 0~100 */
    @Column(name = "score")
    private int value;

    private int sort;
}
