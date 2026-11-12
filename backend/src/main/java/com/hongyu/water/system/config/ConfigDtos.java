package com.hongyu.water.system.config;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ConfigDtos {

    private ConfigDtos() {
    }

    public record ConfigItem(String key, String value, String group, String name, String remark,
            LocalDateTime updatedAt) {
    }

    public record ConfigUpdate(@NotBlank String key,
            @NotNull(message = "参数值不能为空") @Size(max = 500, message = "参数值不超过 500 个字符") String value) {
    }

    public record ScoreRuleDto(Long id,
            @NotBlank(message = "请输入指标编码")
            @Pattern(regexp = "^[a-z][a-zA-Z0-9_]{1,31}$", message = "指标编码为 2~32 位字母、数字或下划线，且以小写字母开头")
            String ruleKey,
            @NotBlank(message = "请输入指标名称") @Size(max = 64) String name,
            @NotNull(message = "请输入权重") @Min(value = 0, message = "权重不能小于 0") @Max(value = 100, message = "权重不能大于 100")
            Integer weight,
            @Size(max = 255) String description,
            Integer sort,
            boolean enabled) {
    }
}
