package com.hongyu.water.weekly;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.hongyu.water.weekly.WeeklyPlan.PlanStatus;
import com.hongyu.water.weekly.WeeklyPlan.ReportStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WeeklyDtos {

    private WeeklyDtos() {
    }

    /** 周信息 */
    public record WeekInfo(LocalDate weekStart, LocalDate weekEnd, int year, int week, String label,
            LocalDateTime planDeadline, LocalDateTime reportDeadline, int offset) {
    }

    public record ItemVO(Long id, Long projectId, String projectCode, String projectName, String content, String target,
            BigDecimal planDays, String remark, boolean extra, Integer completion, String actualContent,
            BigDecimal actualDays, String reason) {
    }

    public record PlanVO(Long id, Long userId, String userName, String deptName, WeekInfo week, PlanStatus planStatus,
            LocalDateTime planSubmittedAt, boolean planOverdue, ReportStatus reportStatus,
            LocalDateTime reportSubmittedAt, boolean reportOverdue, Integer completionRate, BigDecimal planDays,
            String comment, String commentByName, LocalDateTime commentAt, String returnReason, List<ItemVO> items) {
    }

    /** 我的某一周 */
    public record MineResponse(WeekInfo week, PlanVO plan, boolean canEditPlan, boolean canSubmitReport,
            String lockedReason, List<ItemVO> lastWeekUnfinished) {
    }

    public record PlanItemForm(Long projectId,
            @NotBlank(message = "请填写工作内容") @Size(max = 255, message = "工作内容不超过 255 个字") String content,
            @Size(max = 255, message = "计划目标不超过 255 个字") String target,
            @NotNull(message = "请填写计划工时")
            @DecimalMin(value = "0.5", message = "计划工时至少 0.5 天") @DecimalMax(value = "7", message = "计划工时不能超过 7 天")
            BigDecimal planDays,
            @Size(max = 255, message = "备注不超过 255 个字") String remark) {
    }

    public record SavePlanRequest(@NotNull(message = "请选择周次") LocalDate weekStart,
            @NotEmpty(message = "请至少填写一项工作计划") @Size(max = 30, message = "每周最多 30 项") List<@Valid PlanItemForm> items) {
    }

    public record ReportItemForm(@NotNull Long id,
            @NotNull(message = "请填写完成比例") @Min(value = 0, message = "完成比例为 0~100") @Max(value = 100, message = "完成比例为 0~100")
            Integer completion,
            @Size(max = 255, message = "实际完成内容不超过 255 个字") String actualContent,
            @DecimalMin(value = "0", message = "实际工时不能为负数") @DecimalMax(value = "7", message = "实际工时不能超过 7 天")
            BigDecimal actualDays,
            @Size(max = 255, message = "未完成原因不超过 255 个字") String reason) {
    }

    public record ExtraItemForm(Long projectId,
            @NotBlank(message = "请填写计划外工作内容") @Size(max = 255) String content,
            @DecimalMin(value = "0", message = "工时不能为负数") @DecimalMax(value = "7", message = "工时不能超过 7 天")
            BigDecimal actualDays) {
    }

    public record SubmitReportRequest(@NotNull(message = "请选择周次") LocalDate weekStart,
            @NotEmpty List<@Valid ReportItemForm> items,
            @Size(max = 20, message = "计划外工作最多 20 项") List<@Valid ExtraItemForm> extras) {
    }

    public record HistoryRow(Long id, WeekInfo week, int itemCount, BigDecimal planDays, boolean planOverdue,
            PlanStatus planStatus, ReportStatus reportStatus, boolean reportOverdue, Integer completionRate,
            String comment) {
    }

    public record StatRow(Long userId, String userName, String employeeNo, String deptName, Long planId,
            String planStatus, boolean planOverdue, ReportStatus reportStatus, boolean reportOverdue,
            Integer completionRate, BigDecimal planDays, int itemCount, boolean commented) {
    }

    public record StatResponse(WeekInfo week, int total, int planSubmitted, int planOverdue, int reportSubmitted,
            Integer avgRate, List<StatRow> rows) {
    }

    public record CommentRequest(@Size(max = 500, message = "点评不超过 500 个字") String comment) {
    }

    public record ReturnRequest(@NotBlank(message = "请填写退回原因") @Size(max = 255) String reason) {
    }

    /** 工作台待办 */
    public record TodoResponse(WeekInfo week, String planStatus, String reportStatus, boolean planDeadlinePassed,
            boolean reportDeadlinePassed, String returnReason) {
    }
}
