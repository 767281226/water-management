package com.hongyu.water;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 集成测试基类：使用内嵌 PostgreSQL（真实数据库 + Flyway 迁移），整个测试 JVM 共用一个实例。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String ADMIN_PASSWORD = "Admin2026";
    private static final EmbeddedPostgres PG;
    private static String adminToken;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    /** 管理员令牌：首次使用时用初始密码登录并改密。 */
    protected String adminToken() throws Exception {
        if (adminToken == null) {
            JsonNode login = call(post("/api/auth/login"), null, "{\"username\":\"admin\",\"password\":\"admin123\"}");
            String token = login.path("data").path("token").asString();
            JsonNode changed = call(put("/api/auth/password"), token,
                    "{\"oldPassword\":\"admin123\",\"newPassword\":\"" + ADMIN_PASSWORD + "\"}");
            adminToken = changed.path("data").path("token").asString();
        }
        return adminToken;
    }

    protected String login(String username, String password) throws Exception {
        return call(post("/api/auth/login"), null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}").path("data").path("token").asString();
    }

    /** 发送请求并返回响应 JSON（不校验状态码）。 */
    protected JsonNode call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        return json.readTree(raw(req, token, body).getResponse().getContentAsString());
    }

    protected MvcResult raw(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req).andReturn();
    }

    /** 新增用户并返回 ID。 */
    protected long createUser(String admin, String username, String no, String name, String roleCode) throws Exception {
        long roleId = roleId(admin, roleCode);
        JsonNode r = call(post("/api/system/users"), admin, """
                {"username":"%s","employeeNo":"%s","realName":"%s","deptId":2,"roleIds":[%d]}
                """.formatted(username, no, name, roleId));
        if (r.path("code").asInt() != 0) {
            throw new AssertionError(r.toString());
        }
        return r.path("data").asLong();
    }

    /** 已激活（改过初始密码）的测试用户 */
    protected record TestUser(long id, String token) {
    }

    private static int seq = 0;

    /** 新建用户并完成首次改密，返回 ID 和令牌。账号自动加序号避免重复。 */
    protected TestUser activeUser(String admin, String name, long deptId, String... roleCodes) throws Exception {
        int n = ++seq;
        String username = "u" + n + "_" + System.nanoTime() % 100000;
        StringBuilder roles = new StringBuilder();
        for (String c : roleCodes) {
            roles.append(roles.isEmpty() ? "" : ",").append(roleId(admin, c));
        }
        JsonNode r = call(post("/api/system/users"), admin, """
                {"username":"%s","employeeNo":"T%s","realName":"%s","deptId":%d,"roleIds":[%s]}
                """.formatted(username, username, name, deptId, roles));
        if (r.path("code").asInt() != 0) {
            throw new AssertionError(r.toString());
        }
        String t = login(username, "Hy123456");
        String token = call(put("/api/auth/password"), t, "{\"oldPassword\":\"Hy123456\",\"newPassword\":\"Test2026a\"}")
                .path("data").path("token").asString();
        return new TestUser(r.path("data").asLong(), token);
    }

    protected long roleId(String admin, String code) throws Exception {
        for (JsonNode role : call(get("/api/system/roles"), admin, null).path("data")) {
            if (role.path("code").asString().equals(code)) {
                return role.path("id").asLong();
            }
        }
        throw new IllegalStateException("role not found: " + code);
    }
}
