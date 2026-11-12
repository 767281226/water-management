package com.hongyu.water.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.Test;

import com.hongyu.water.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        assertThat(raw(get("/api/auth/me"), null, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(raw(get("/api/system/users"), "not-a-token", null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void adminHasAllPermissionsAfterChangingInitialPassword() throws Exception {
        JsonNode me = call(get("/api/auth/me"), adminToken(), null).path("data");
        assertThat(me.path("superAdmin").asBoolean()).isTrue();
        assertThat(me.path("mustChangePwd").asBoolean()).isFalse();
        assertThat(me.path("dataScope").asString()).isEqualTo("ALL");
        assertThat(me.path("permissions").toString()).contains("system:user:create", "doc:manage", "score:publish");
    }

    @Test
    void initialPasswordMustBeChangedBeforeUsingTheSystem() throws Exception {
        String admin = adminToken();
        createUser(admin, "zhangsan", "HY1001", "张三", "staff");

        JsonNode login = call(post("/api/auth/login"), null, "{\"username\":\"zhangsan\",\"password\":\"Hy123456\"}");
        assertThat(login.path("data").path("mustChangePwd").asBoolean()).isTrue();
        String token = login.path("data").path("token").asString();

        // 改密前访问业务接口被拦截
        JsonNode blocked = call(get("/api/system/roles"), token, null);
        assertThat(blocked.path("code").asInt()).isEqualTo(403);
        assertThat(blocked.path("message").asString()).contains("修改初始密码");

        // 新密码不符合规则
        JsonNode weak = call(put("/api/auth/password"), token, "{\"oldPassword\":\"Hy123456\",\"newPassword\":\"12345678\"}");
        assertThat(weak.path("message").asString()).contains("字母和数字");

        JsonNode changed = call(put("/api/auth/password"), token, "{\"oldPassword\":\"Hy123456\",\"newPassword\":\"Zhang2026\"}");
        String newToken = changed.path("data").path("token").asString();

        // 旧令牌失效，新令牌可用；普通员工没有系统管理权限
        assertThat(raw(get("/api/auth/me"), token, null).getResponse().getStatus()).isEqualTo(401);
        JsonNode me = call(get("/api/auth/me"), newToken, null).path("data");
        assertThat(me.path("permissions").toString()).contains("weekly:plan").doesNotContain("system:user");
        assertThat(raw(get("/api/system/users"), newToken, null).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void accountIsLockedAfterTooManyWrongPasswords() throws Exception {
        String admin = adminToken();
        long id = createUser(admin, "lockme", "HY1999", "锁定测试", "staff");
        for (int i = 0; i < 4; i++) {
            JsonNode r = call(post("/api/auth/login"), null, "{\"username\":\"lockme\",\"password\":\"wrong-pwd1\"}");
            assertThat(r.path("message").asString()).contains("再输错");
        }
        JsonNode fifth = call(post("/api/auth/login"), null, "{\"username\":\"lockme\",\"password\":\"wrong-pwd1\"}");
        assertThat(fifth.path("message").asString()).contains("已锁定");
        // 锁定期间正确密码也无法登录
        JsonNode locked = call(post("/api/auth/login"), null, "{\"username\":\"lockme\",\"password\":\"Hy123456\"}");
        assertThat(locked.path("message").asString()).contains("已锁定");

        // 管理员解锁后可登录
        call(put("/api/system/users/" + id + "/unlock"), admin, null);
        assertThat(login("lockme", "Hy123456")).isNotBlank();
    }

    /** 经 Nginx / Vite 代理访问时浏览器会带 Origin 头，默认不启用 CORS，不能被拒绝。 */
    @Test
    void requestsWithOriginHeaderAreNotRejectedByDefault() throws Exception {
        adminToken();
        var res = raw(post("/api/auth/login").header("Origin", "http://192.168.1.10"), null,
                "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}").getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void loginByEmployeeNoIsSupported() throws Exception {
        createUser(adminToken(), "lisi", "HY1002", "李四", "manager");
        assertThat(login("HY1002", "Hy123456")).isNotBlank();
    }
}
