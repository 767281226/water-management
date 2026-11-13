package com.hongyu.water.system.config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PasswordPolicy;
import com.hongyu.water.system.config.ConfigDtos.ConfigItem;
import com.hongyu.water.system.config.ConfigDtos.ConfigUpdate;
import com.hongyu.water.system.config.ConfigDtos.ScoreRuleDto;

/**
 * 系统参数（填报规则、安全设置）与月度考评指标。
 */
@Service
public class ConfigService {

    private static final Set<String> NOTIFY_CHANNELS = Set.of("SITE");

    private final SysConfigRepository configRepo;
    private final ScoreRuleRepository ruleRepo;

    public ConfigService(SysConfigRepository configRepo, ScoreRuleRepository ruleRepo) {
        this.configRepo = configRepo;
        this.ruleRepo = ruleRepo;
    }

    // ---------------- 参数 ----------------

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return configRepo.findById(key).map(SysConfig::getConfigValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 系统内部维护的参数（不在界面上修改） */
    @Transactional
    public void setInternal(String key, String value) {
        configRepo.findById(key).ifPresent(c -> c.setConfigValue(value));
    }

    public String initPassword() {
        return get("security.init-password", "Hy123456");
    }

    @Transactional(readOnly = true)
    public List<ConfigItem> list(String group) {
        return configRepo.findByGroupCodeOrderBySortAsc(group).stream()
                .map(c -> new ConfigItem(c.getConfigKey(), c.getConfigValue(), c.getGroupCode(), c.getName(),
                        c.getRemark(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public void update(List<ConfigUpdate> items) {
        for (ConfigUpdate it : items) {
            SysConfig c = configRepo.findById(it.key()).filter(x -> !"internal".equals(x.getGroupCode()))
                    .orElseThrow(() -> new BizException("参数不存在：" + it.key()));
            String v = it.value().trim();
            validate(c, v);
            c.setConfigValue(v);
        }
    }

    private void validate(SysConfig c, String v) {
        if (v.isEmpty()) {
            throw new BizException(c.getName() + "不能为空");
        }
        switch (c.getConfigKey()) {
            case "security.init-password" -> PasswordPolicy.check(v);
            case "security.login.max-fail" -> requireRange(c, v, 1, 20);
            case "security.login.lock-minutes" -> requireRange(c, v, 1, 1440);
            case "doc.download.valid-days" -> requireRange(c, v, 1, 365);
            case "doc.download.max-times" -> requireRange(c, v, 1, 20);
            case "doc.new-days" -> requireRange(c, v, 0, 365);
            case "fill.plan.deadline", "fill.report.deadline" -> {
                if (!v.matches("^(下)?周[一二三四五六日天]\\s*\\d{1,2}[:：][0-5]\\d$")) {
                    throw new BizException(c.getName() + "格式应为「周X HH:mm」或「下周X HH:mm」，例如：周一 23:59");
                }
            }
            case "project.types" -> {
                long n = java.util.Arrays.stream(v.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).count();
                if (n == 0) {
                    throw new BizException("至少需要一个项目类型");
                }
            }
            case "project.code-prefix" -> {
                if (!v.matches("^[A-Za-z0-9]{1,10}$")) {
                    throw new BizException("项目编号前缀为 1~10 位字母或数字");
                }
            }
            case "fill.notify.channels" -> {
                for (String ch : v.split(",")) {
                    if (!NOTIFY_CHANNELS.contains(ch.trim())) {
                        throw new BizException("提醒渠道目前仅支持站内消息（SITE）");
                    }
                }
            }
            default -> {
                if (v.length() > 100) {
                    throw new BizException(c.getName() + "不超过 100 个字符");
                }
            }
        }
    }

    private static void requireRange(SysConfig c, String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new BizException(c.getName() + "应为 " + min + "~" + max + " 之间的整数");
        }
    }

    // ---------------- 考评指标 ----------------

    @Transactional(readOnly = true)
    public List<ScoreRuleDto> scoreRules() {
        return ruleRepo.findAllByOrderBySortAscIdAsc().stream().map(r -> new ScoreRuleDto(r.getId(), r.getRuleKey(),
                r.getName(), r.getWeight(), r.getDescription(), r.getSort(), r.isEnabled())).toList();
    }

    /** 整体保存：新增 / 修改 / 删除，启用指标的权重合计必须为 100。 */
    @Transactional
    public void saveScoreRules(List<ScoreRuleDto> rules) {
        if (rules.stream().noneMatch(ScoreRuleDto::enabled)) {
            throw new BizException("至少需要启用一个考评指标");
        }
        int sum = rules.stream().filter(ScoreRuleDto::enabled).mapToInt(ScoreRuleDto::weight).sum();
        if (sum != 100) {
            throw new BizException("启用指标的权重合计必须为 100%，当前为 " + sum + "%");
        }
        Set<String> keys = new HashSet<>();
        for (ScoreRuleDto r : rules) {
            if (!keys.add(r.ruleKey())) {
                throw new BizException("指标编码重复：" + r.ruleKey());
            }
        }
        Map<Long, ScoreRule> existing = ruleRepo.findAll().stream()
                .collect(Collectors.toMap(ScoreRule::getId, Function.identity()));
        Set<Long> keep = rules.stream().map(ScoreRuleDto::id).filter(Objects::nonNull).collect(Collectors.toSet());
        // 先删除，避免编码唯一约束冲突
        existing.values().stream().filter(r -> !keep.contains(r.getId())).forEach(ruleRepo::delete);
        ruleRepo.flush();
        int i = 0;
        for (ScoreRuleDto dto : rules) {
            ScoreRule r = dto.id() == null ? new ScoreRule() : existing.get(dto.id());
            if (r == null) {
                throw new BizException("考评指标不存在：" + dto.id());
            }
            r.setRuleKey(dto.ruleKey());
            r.setName(dto.name().trim());
            r.setWeight(dto.weight());
            r.setDescription(dto.description());
            r.setSort(dto.sort() == null ? ++i : dto.sort());
            r.setEnabled(dto.enabled());
            ruleRepo.save(r);
        }
    }
}
