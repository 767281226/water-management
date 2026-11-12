package com.hongyu.water.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.Test;

import com.hongyu.water.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

class SystemManagementIntegrationTest extends IntegrationTestBase {

    private long adminId() throws Exception {
        return call(get("/api/auth/me"), adminToken(), null).path("data").path("id").asLong();
    }

    @Test
    void builtinAdminIsProtected() throws Exception {
        String admin = adminToken();
        long id = adminId();
        assertThat(call(delete("/api/system/users/" + id), admin, null).path("message").asString()).contains("不可删除");
        assertThat(call(put("/api/system/users/" + id + "/status"), admin, "{\"status\":\"DISABLED\"}")
                .path("message").asString()).contains("不可停用");
        long staffRole = roleId(admin, "staff");
        JsonNode r = call(put("/api/system/users/" + id), admin, """
                {"username":"admin","employeeNo":"HY0000","realName":"系统管理员","roleIds":[%d]}""".formatted(staffRole));
        assertThat(r.path("message").asString()).contains("必须保留超级管理员角色");
    }

    @Test
    void userCrudAndDisableRevokesLogin() throws Exception {
        String admin = adminToken();
        long id = createUser(admin, "wangwu", "HY1003", "王五", "staff");

        // 重复账号 / 工号
        long staffRole = roleId(admin, "staff");
        JsonNode dup = call(post("/api/system/users"), admin, """
                {"username":"WANGWU","employeeNo":"HY9000","realName":"重复","roleIds":[%d]}""".formatted(staffRole));
        assertThat(dup.path("message").asString()).contains("已存在");

        // 列表筛选
        JsonNode page = call(get("/api/system/users").param("keyword", "王五"), admin, null).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("records").get(0).path("deptName").asString()).isEqualTo("规划设计一部");

        String token = login("wangwu", "Hy123456");
        call(put("/api/system/users/" + id + "/status"), admin, "{\"status\":\"DISABLED\"}");
        assertThat(raw(get("/api/auth/me"), token, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(post("/api/auth/login"), null, "{\"username\":\"wangwu\",\"password\":\"Hy123456\"}")
                .path("message").asString()).contains("停用");

        call(put("/api/system/users/" + id + "/status"), admin, "{\"status\":\"ENABLED\"}");
        JsonNode reset = call(put("/api/system/users/" + id + "/reset-password"), admin, null);
        assertThat(reset.path("data").path("password").asString()).isEqualTo("Hy123456");

        assertThat(call(delete("/api/system/users/" + id), admin, null).path("code").asInt()).isZero();
        assertThat(call(get("/api/system/users").param("keyword", "王五"), admin, null)
                .path("data").path("total").asLong()).isZero();
        // 删除后账号可以重新使用
        createUser(admin, "wangwu", "HY1003", "王五", "staff");
    }

    @Test
    void nonSuperAdminCannotGrantAdminRole() throws Exception {
        String admin = adminToken();
        // 创建一个拥有用户管理权限的普通管理角色
        JsonNode role = call(post("/api/system/roles"), admin, """
                {"code":"hr","name":"人事","dataScope":"ALL","permissions":["system:user:create"]}""");
        long hrRole = role.path("data").asLong();
        createUserWithRole(admin, "hr01", "HY5001", "人事专员", hrRole);
        String hr = changeInitialPassword("hr01");

        // 自动补齐上级菜单：system:user、system
        JsonNode me = call(get("/api/auth/me"), hr, null).path("data");
        assertThat(me.path("permissions").toString()).contains("\"system\"", "\"system:user\"");

        long adminRole = roleId(admin, "admin");
        JsonNode r = call(post("/api/system/users"), hr, """
                {"username":"evil","employeeNo":"HY6666","realName":"越权","roleIds":[%d]}""".formatted(adminRole));
        assertThat(r.path("code").asInt()).isEqualTo(403);
        // 没有重置密码权限
        assertThat(raw(put("/api/system/users/" + adminId() + "/reset-password"), hr, null).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    void roleRules() throws Exception {
        String admin = adminToken();
        long staffRole = roleId(admin, "staff");
        assertThat(call(delete("/api/system/roles/" + staffRole), admin, null).path("message").asString())
                .contains("内置角色不可删除");

        long id = call(post("/api/system/roles"), admin, """
                {"code":"auditor","name":"审计","dataScope":"SELF","permissions":["system:log"]}""").path("data").asLong();
        assertThat(call(post("/api/system/roles"), admin, """
                {"code":"auditor","name":"重复","dataScope":"SELF"}""").path("message").asString()).contains("已存在");
        assertThat(call(post("/api/system/roles"), admin, """
                {"code":"bad","name":"x","dataScope":"SELF","permissions":["no:such"]}""").path("message").asString())
                .contains("权限点不存在");

        JsonNode tree = call(get("/api/system/permissions/tree"), admin, null).path("data");
        assertThat(tree.toString()).contains("system:user:reset-pwd", "doc:approve");
        assertThat(call(delete("/api/system/roles/" + id), admin, null).path("code").asInt()).isZero();
    }

    @Test
    void deptTreeAndRules() throws Exception {
        String admin = adminToken();
        JsonNode tree = call(get("/api/system/depts/tree"), admin, null).path("data");
        assertThat(tree.get(0).path("name").asString()).isEqualTo("宏禹公司");
        assertThat(tree.get(0).path("children").size()).isEqualTo(3);

        long id = call(post("/api/system/depts"), admin, "{\"parentId\":1,\"name\":\"测绘部\",\"sort\":9}").path("data").asLong();
        assertThat(call(post("/api/system/depts"), admin, "{\"parentId\":1,\"name\":\"测绘部\"}").path("message").asString())
                .contains("已存在");
        assertThat(call(put("/api/system/depts/1"), admin, "{\"parentId\":" + id + ",\"name\":\"宏禹公司\"}")
                .path("message").asString()).contains("下级部门");
        // 有下级部门 / 有人员的部门不能删除
        assertThat(call(delete("/api/system/depts/1"), admin, null).path("message").asString()).contains("下级部门");
        assertThat(call(delete("/api/system/depts/" + id), admin, null).path("code").asInt()).isZero();
    }

    @Test
    void configAndScoreRules() throws Exception {
        String admin = adminToken();
        JsonNode fill = call(get("/api/system/configs").param("group", "fill"), admin, null).path("data");
        assertThat(fill.size()).isGreaterThanOrEqualTo(6);

        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"security.init-password\",\"value\":\"123\"}]")
                .path("message").asString()).contains("密码");
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"security.login.max-fail\",\"value\":\"99\"}]")
                .path("message").asString()).contains("1~20");
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"fill.plan.deadline\",\"value\":\"周二 12:00\"}]")
                .path("code").asInt()).isZero();

        JsonNode rules = call(get("/api/system/score-rules"), admin, null).path("data");
        assertThat(rules.size()).isEqualTo(4);
        String bad = "[{\"ruleKey\":\"progress\",\"name\":\"进度\",\"weight\":50,\"enabled\":true}]";
        assertThat(call(put("/api/system/score-rules"), admin, bad).path("message").asString()).contains("100%");
        String ok = """
                [{"id":%d,"ruleKey":"progress","name":"进度","weight":60,"enabled":true},
                 {"ruleKey":"safety","name":"安全生产","weight":40,"enabled":true}]""".formatted(rules.get(0).path("id").asLong());
        assertThat(call(put("/api/system/score-rules"), admin, ok).path("code").asInt()).isZero();
        assertThat(call(get("/api/system/score-rules"), admin, null).path("data").size()).isEqualTo(2);
    }

    @Test
    void operationsAreLoggedWithPasswordsMasked() throws Exception {
        String admin = adminToken();
        JsonNode logs = call(get("/api/system/logs/oper").param("module", "个人中心"), admin, null).path("data");
        assertThat(logs.path("total").asLong()).isPositive();
        assertThat(logs.toString()).doesNotContain(ADMIN_PASSWORD).contains("******");

        JsonNode login = call(get("/api/system/logs/login").param("keyword", "admin"), admin, null).path("data");
        assertThat(login.path("total").asLong()).isPositive();
    }

    private void createUserWithRole(String admin, String username, String no, String name, long roleId) throws Exception {
        JsonNode r = call(post("/api/system/users"), admin, """
                {"username":"%s","employeeNo":"%s","realName":"%s","roleIds":[%d]}""".formatted(username, no, name, roleId));
        assertThat(r.path("code").asInt()).as(r.toString()).isZero();
    }

    private String changeInitialPassword(String username) throws Exception {
        String t = login(username, "Hy123456");
        return call(put("/api/auth/password"), t, "{\"oldPassword\":\"Hy123456\",\"newPassword\":\"Change2026\"}")
                .path("data").path("token").asString();
    }
}
