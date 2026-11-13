package com.hongyu.water.score;

import java.math.BigDecimal;
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
 * 月度考评记录：每个存档项目一条，进入存档当月的考评清单。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_score")
public class Score extends BaseEntity {

    public enum Status { PENDING, SCORED, PUBLISHED }

    @Column(name = "project_id")
    private Long projectId;

    /** YYYY-MM */
    private String month;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private BigDecimal total;
    private String comment;

    @Column(name = "scorer_id")
    private Long scorerId;

    @Column(name = "scored_at")
    private LocalDateTime scoredAt;

    @Column(name = "publisher_id")
    private Long publisherId;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "score", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sort asc, id asc")
    private List<ScoreItem> items = new ArrayList<>();

    public boolean published() {
        return status == Status.PUBLISHED;
    }
}
