package com.hongyu.water.project;

import java.util.Map;
import java.util.Set;

/**
 * 项目状态：未开始 → 进行中 ⇄ 暂停 → 已完成 → 已存档；进行中 / 暂停可终止。
 */
public enum ProjectStatus {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    PAUSED("暂停"),
    COMPLETED("已完成"),
    TERMINATED("终止"),
    ARCHIVED("已存档");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 手动可切换的目标状态（存档走单独的申请 / 确认流程）。 */
    private static final Map<ProjectStatus, Set<ProjectStatus>> TRANSITIONS = Map.of(
            NOT_STARTED, Set.of(IN_PROGRESS, TERMINATED),
            IN_PROGRESS, Set.of(PAUSED, COMPLETED, TERMINATED),
            PAUSED, Set.of(IN_PROGRESS, TERMINATED),
            COMPLETED, Set.of(IN_PROGRESS),
            TERMINATED, Set.of(),
            ARCHIVED, Set.of());

    public boolean canChangeTo(ProjectStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 周报中可以选择的项目状态 */
    public boolean fillable() {
        return this == NOT_STARTED || this == IN_PROGRESS || this == PAUSED || this == COMPLETED;
    }
}
