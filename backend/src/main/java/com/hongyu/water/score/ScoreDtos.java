package com.hongyu.water.score;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ScoreDtos {

    private ScoreDtos() {
    }

    public record RuleRef(String ruleKey, String name, int weight, String description) {
    }

    public record ItemVO(String ruleKey, String ruleName, int weight, int score) {
    }

    /** 系统自动统计的参考数据 */
    public record Reference(Integer planDuration, Integer actualDuration, Integer delayDays, long weeklyCount,
            BigDecimal totalDays, Integer avgCompletion, long overduePlans) {
    }

    /** 个人贡献：按周报工时占比；无周报数据时负责人 40%，其余成员平分 */
    public record Share(Long userId, String name, BigDecimal days, double ratio, boolean byHours) {
    }

    public record ScoreActions(boolean grade, boolean publish, boolean unpublish) {
    }

    public record ProjectBrief(Long id, String code, String name, String type, String deptName, String leaderName,
            LocalDateTime archivedAt, LocalDate startDate, LocalDate planEndDate, LocalDate actualEndDate) {
    }

    /**
     * @param detailVisible 是否可以看到得分（已发布，或有打分 / 发布权限）
     */
    public record ScoreVO(Long id, String month, Score.Status status, ProjectBrief project, boolean detailVisible,
            BigDecimal total, List<ItemVO> items, String comment, String scorerName, LocalDateTime scoredAt,
            String publisherName, LocalDateTime publishedAt, Reference reference, List<Share> shares,
            ScoreActions actions) {
    }

    public record MonthResponse(String month, int pending, int scored, int published, List<RuleRef> rules,
            List<ScoreVO> list) {
    }

    public record GradeItem(@NotBlank String ruleKey,
            @NotNull(message = "请填写得分") @Min(value = 0, message = "得分为 0~100") @Max(value = 100, message = "得分为 0~100")
            Integer score) {
    }

    public record GradeRequest(@NotEmpty(message = "请填写各项指标得分") List<@Valid GradeItem> items,
            @Size(max = 500, message = "评语不超过 500 个字") String comment) {
    }

    public record UnpublishRequest(@NotBlank(message = "请填写撤回原因") @Size(max = 255) String reason) {
    }

    public record TodoResponse(int toGrade, int toPublish) {
    }
}
