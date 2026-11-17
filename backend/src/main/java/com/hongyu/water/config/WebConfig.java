package com.hongyu.water.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import com.hongyu.water.security.MustChangePasswordInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final MustChangePasswordInterceptor mustChangePasswordInterceptor;
    private final AppProperties props;

    public WebConfig(MustChangePasswordInterceptor mustChangePasswordInterceptor, AppProperties props) {
        this.mustChangePasswordInterceptor = mustChangePasswordInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mustChangePasswordInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
    }

    private boolean staticEnabled() {
        String dir = props.web() == null ? null : props.web().staticDir();
        return dir != null && !dir.isBlank() && Files.isRegularFile(Path.of(dir).resolve("index.html"));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (staticEnabled()) {
            // 资源处理器不处理空路径，根路径单独转发
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }

    /**
     * 配置 app.web.static-dir 时由后端直接提供前端页面（Windows 单服务部署）：
     * 带哈希的静态资源长期缓存；前端路由（如 /project/ledger）回退到 index.html；/api 不受影响。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = props.web() == null ? null : props.web().staticDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path root = Path.of(dir).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("index.html"))) {
            log.warn("app.web.static-dir={} 下没有 index.html，不提供前端页面", root);
            return;
        }
        log.info("由后端提供前端页面：{}", root);
        String location = root.toUri().toString();
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource loc) throws IOException {
                        if (path.startsWith("api/") || path.startsWith("actuator/")) {
                            return null;
                        }
                        Resource r = loc.createRelative(path);
                        if (r.exists() && r.isReadable() && !path.isEmpty()) {
                            return r;
                        }
                        // 前端路由：没有对应文件且不像静态文件时返回 index.html
                        return path.contains(".") && !path.endsWith(".html") ? null : loc.createRelative("index.html");
                    }
                });
    }
}
