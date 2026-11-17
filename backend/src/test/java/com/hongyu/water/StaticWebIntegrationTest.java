package com.hongyu.water;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 后端直接提供前端页面（Windows 单服务部署）：静态文件、前端路由回退、/api 不受影响。
 */
class StaticWebIntegrationTest extends IntegrationTestBase {

    private static final Path WEB;

    static {
        try {
            WEB = Files.createTempDirectory("hy-web");
            Files.writeString(WEB.resolve("index.html"), "<html>index-page</html>");
            Files.createDirectories(WEB.resolve("assets"));
            Files.writeString(WEB.resolve("assets/app-abc123.js"), "console.log('app')");
            Files.writeString(WEB.resolve("favicon.svg"), "<svg/>");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void web(DynamicPropertyRegistry r) {
        r.add("app.web.static-dir", WEB::toString);
    }

    private MockHttpServletResponse fetch(String path) throws Exception {
        return raw(get(path), null, null).getResponse();
    }

    @Test
    void servesFrontendWithSpaFallback() throws Exception {
        // 根路径转发到 index.html（MockMvc 不执行 forward，只校验转发目标）
        assertThat(fetch("/").getForwardedUrl()).isEqualTo("/index.html");
        assertThat(fetch("/index.html").getContentAsString()).contains("index-page");
        // 前端路由回退到 index.html
        MockHttpServletResponse route = fetch("/project/ledger/12");
        assertThat(route.getStatus()).isEqualTo(200);
        assertThat(route.getContentAsString()).contains("index-page");
        // 静态资源，长期缓存
        MockHttpServletResponse js = fetch("/assets/app-abc123.js");
        assertThat(js.getContentAsString()).contains("console.log");
        assertThat(js.getHeader("Cache-Control")).contains("max-age=31536000");
        assertThat(fetch("/favicon.svg").getContentAsString()).isEqualTo("<svg/>");
        // 不存在的静态文件返回 404，而不是 index.html
        assertThat(fetch("/assets/missing.js").getStatus()).isEqualTo(404);
        // 接口不受影响
        assertThat(fetch("/api/auth/me").getStatus()).isEqualTo(401);
        assertThat(fetch("/api/not-exist").getStatus()).isIn(401, 404);
        assertThat(fetch("/actuator/health").getContentAsString()).contains("UP");
    }
}
