package com.hongyu.water.project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectQuery(String keyword, ProjectStatus status, String type, Long deptId, Boolean mine,
            Integer page, Integer size) {
    }

    public record ProjectForm(
            @Size(max = 32, message = "项目编号不超过 32 个字符") String code,
            @NotBlank(message = "请输入项目名称") @Size(max = 128, message = "项目名称不超过 128 个字") String name,
            @NotBlank(message = "请选择项目类型") String type,
            @Size(max = 128, message = "委托单位不超过 128 个字") String client,
            @NotNull(message = "请选择项目负责人") Long leaderId,
            Long deptId,
            @DecimalMin(value = "0", message = "合同金额不能为负数") @DecimalMax(value = "999999999", message = "合同金额过大")
            BigDecimal contractAmount,
            LocalDate startDate,
            @NotNull(message = "请选择计划完成日期") LocalDate planEndDate,
            Set<Long> memberIds,
            @Size(max = 500, message = "备注不超过 500 个字") String remark) {
    }

    public record StatusRequest(@NotNull(message = "请选择状态") ProjectStatus status) {
    }

    public record RejectRequest(@NotBlank(message = "请填写退回原因") @Size(max = 255) String reason) {
    }

    public record UserRef(Long id, String name) {
    }

    /** 当前用户对该项目可执行的操作 */
    public record ProjectActions(boolean edit, boolean changeStatus, boolean applyArchive, boolean confirmArchive,
            boolean delete) {
    }

    public record ProjectVO(Long id, String code, String name, String type, String client, Long leaderId,
            String leaderName, Long deptId, String deptName, BigDecimal contractAmount, LocalDate startDate,
            LocalDate planEndDate, LocalDate actualEndDate, ProjectStatus status, String statusLabel,
            boolean archiveApplied, String archiveRejectReason, LocalDateTime archivedAt, String archivedByName,
            List<UserRef> members, String remark, LocalDateTime createdAt, ProjectActions actions) {
    }

    public record ProjectOption(Long id, String code, String name, ProjectStatus status) {
    }

    /** 项目周报动态 */
    public record ProjectWeeklyEntry(Long itemId, LocalDate weekStart, Long userId, String userName, String content,
            String target, BigDecimal planDays, Integer completion, String actualContent, BigDecimal actualDays,
            String reason, boolean extra, boolean reported) {
    }

    public record ProjectDetail(ProjectVO project, long weeklyCount, BigDecimal totalDays, int memberCount,
            List<ProjectWeeklyEntry> recentEntries) {
    }
}
