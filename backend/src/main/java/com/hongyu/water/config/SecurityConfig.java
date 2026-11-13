package com.hongyu.water.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hongyu.water.security.TokenAuthenticationConverter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 安全配置：无状态 JWT（Bearer Token），接口权限使用 @PreAuthorize("hasAuthority('权限点')")。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties props;

    public SecurityConfig(AppProperties props) {
        this.props = props;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticationConverter converter) throws Exception {
        // 前端通过同源代理（Vite / Nginx）访问 /api，默认不需要 CORS；只有显式配置 app.cors.allowed-origins 才启用
        List<String> origins = props.cors() != null && props.cors().allowedOrigins() != null
                ? props.cors().allowedOrigins().stream().filter(o -> !o.isBlank()).toList()
                : List.of();
        if (origins.isEmpty()) {
            http.cors(c -> c.disable());
        } else {
            http.cors(c -> c.configurationSource(corsConfigurationSource(origins)));
        }
        // 资料在线预览通过同源 iframe 加载，允许同源嵌入（默认 DENY 会导致预览空白）
        http.headers(h -> h.frameOptions(f -> f.sameOrigin()));
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 资料预览 / 下载：凭短时凭证访问（凭证在登录状态下签发并已校验权限）
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()).accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKey secretKey() {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 个字节");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static CorsConfigurationSource corsConfigurationSource(List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }

    private static AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> writeJson(res, 401, "登录已失效，请重新登录");
    }

    private static AccessDeniedHandler accessDeniedHandler() {
        return (req, res, ex) -> writeJson(res, 403, "没有该操作的权限");
    }

    private static void writeJson(HttpServletResponse res, int status, String msg) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":" + status + ",\"message\":\"" + msg + "\",\"data\":null}");
    }
}
