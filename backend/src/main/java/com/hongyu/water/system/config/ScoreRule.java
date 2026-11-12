package com.hongyu.water.system.config;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 月度考评指标。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_score_rule")
public class ScoreRule extends BaseEntity {

    @Column(name = "rule_key")
    private String ruleKey;

    private String name;

    /** 权重（%），启用指标合计必须为 100 */
    private int weight;

    private String description;
    private int sort;
    private boolean enabled = true;
}
