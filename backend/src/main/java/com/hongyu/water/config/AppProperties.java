package com.hongyu.water.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置（application.yml 中的 app.*）。
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Cors cors) {

    public record Jwt(String secret, Duration expire) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
