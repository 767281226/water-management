package com.hongyu.water.system.config;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.system.config.ConfigDtos.ConfigItem;
import com.hongyu.water.system.config.ConfigDtos.ConfigUpdate;
import com.hongyu.water.system.config.ConfigDtos.ScoreRuleDto;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Validated
@RestController
@RequestMapping("/api/system")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /** group：fill 填报规则 / security 安全设置 */
    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('system:config')")
    public ApiResponse<List<ConfigItem>> list(@RequestParam String group) {
        return ApiResponse.ok(configService.list(group));
    }

    @OperLog(module = "参数配置", action = "修改系统参数")
    @PutMapping("/configs")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public ApiResponse<Void> update(@RequestBody @NotEmpty List<@Valid ConfigUpdate> items) {
        configService.update(items);
        return ApiResponse.ok();
    }

    @GetMapping("/score-rules")
    @PreAuthorize("hasAuthority('system:config')")
    public ApiResponse<List<ScoreRuleDto>> scoreRules() {
        return ApiResponse.ok(configService.scoreRules());
    }

    @OperLog(module = "参数配置", action = "修改考评指标")
    @PutMapping("/score-rules")
    @PreAuthorize("hasAuthority('system:config:edit')")
    public ApiResponse<Void> saveScoreRules(@RequestBody @NotEmpty(message = "至少需要一个考评指标") List<@Valid ScoreRuleDto> rules) {
        configService.saveScoreRules(rules);
        return ApiResponse.ok();
    }
}
