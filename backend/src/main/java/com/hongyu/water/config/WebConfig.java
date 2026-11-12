package com.hongyu.water.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hongyu.water.security.MustChangePasswordInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MustChangePasswordInterceptor mustChangePasswordInterceptor;

    public WebConfig(MustChangePasswordInterceptor mustChangePasswordInterceptor) {
        this.mustChangePasswordInterceptor = mustChangePasswordInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mustChangePasswordInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
    }
}
