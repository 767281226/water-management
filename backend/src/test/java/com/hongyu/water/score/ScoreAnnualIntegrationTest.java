package com.hongyu.water.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.hongyu.water.IntegrationTestBase;
import com.hongyu.water.weekly.WeekRules;

import tools.jackson.databind.JsonNode;

/**
 * 月度考评与年度汇总。
 */
class ScoreAnnualIntegrationTest extends IntegrationTestBase {

    private static final String MONTH = String.format("%d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());

    private JsonNode findByProject(JsonNode monthResp, long projectId) {
        for (JsonNode s : monthResp.path("list")) {
            if (s.path("project").path("id").asLong() == projectId) {
                return s;
            }
        }
        return null;
    }

    @Test
    void archiveGradePublishAndAnnual() throws Exception {
        String admin = adminToken();
        TestUser mgr = activeUser(admin, "考评经理", 2, "staff", "manager");
        TestUser boss = activeUser(admin, "考评领导", 1, "staff", "leader");
        TestUser lead = activeUser(admin, "考评负责人", 2, "staff");
        TestUser mem = activeUser(admin, "考评成员", 2, "staff");
        TestUser outsider = activeUser(admin, "考评外人", 3, "staff");

        long pid = call(post("/api/projects"), mgr.token(), """
                {"name":"考评测试项目","type":"咨询","leaderId":%d,"deptId":2,"startDate":"%s","planEndDate":"%s","memberIds":[%d]}"""
                .formatted(lead.id(), LocalDate.now().minusMonths(2), LocalDate.now().plusDays(10), mem.id())).path("data").asLong();
        // 成员填报周报：2 天，完成 100%
        JsonNode plan = call(put("/api/weekly/plan"), mem.token(), """
                {"weekStart":"%s","items":[{"projectId":%d,"content":"安全鉴定报告","planDays":2}]}"""
                .formatted(WeekRules.mondayOf(LocalDate.now()), pid)).path("data");
        call(put("/api/weekly/report"), mem.token(), """
                {"weekStart":"%s","items":[{"id":%d,"completion":100}]}"""
                .formatted(WeekRules.mondayOf(LocalDate.now()), plan.path("items").get(0).path("id").asLong()));

        // 完成 → 申请存档 → 确认存档：自动进入当月考评
        call(put("/api/projects/" + pid + "/status"), lead.token(), "{\"status\":\"IN_PROGRESS\"}");
        call(put("/api/projects/" + pid + "/status"), lead.token(), "{\"status\":\"COMPLETED\"}");
        call(post("/api/projects/" + pid + "/archive/apply"), lead.token(), null);
        call(post("/api/projects/" + pid + "/archive/confirm"), mgr.token(), null);

        JsonNode s = findByProject(call(get("/api/scores").param("month", MONTH), mgr.token(), null).path("data"), pid);
        assertThat(s).isNotNull();
        long sid = s.path("id").asLong();
        assertThat(s.path("status").asString()).isEqualTo("PENDING");
        assertThat(s.path("actions").path("grade").asBoolean()).isTrue();
        assertThat(s.path("actions").path("publish").asBoolean()).isFalse();
        assertThat(s.path("reference").path("weeklyCount").asLong()).isEqualTo(1);
        assertThat(s.path("reference").path("avgCompletion").asInt()).isEqualTo(100);
        assertThat(s.path("reference").path("delayDays").asInt()).isEqualTo(-10);
        // 贡献按工时：成员 100%
        assertThat(s.path("shares").get(0).path("userId").asLong()).isEqualTo(mem.id());
        assertThat(s.path("shares").get(0).path("ratio").asDouble()).isEqualTo(1.0);

        // 成员能看到考评记录，但未发布前看不到得分；无关人员看不到
        JsonNode memView = findByProject(call(get("/api/scores").param("month", MONTH), mem.token(), null).path("data"), pid);
        assertThat(memView.path("detailVisible").asBoolean()).isFalse();
        assertThat(findByProject(call(get("/api/scores").param("month", MONTH), outsider.token(), null).path("data"), pid)).isNull();
        assertThat(call(get("/api/scores/" + sid), outsider.token(), null).path("code").asInt()).isEqualTo(403);

        // 打分：按当前启用的指标
        JsonNode rules = call(get("/api/scores/rules"), mgr.token(), null).path("data");
        StringBuilder items = new StringBuilder();
        BigDecimal expected = BigDecimal.ZERO;
        for (int i = 0; i < rules.size(); i++) {
            int v = i == 0 ? 95 : 80;
            items.append(i == 0 ? "" : ",").append("{\"ruleKey\":\"%s\",\"score\":%d}".formatted(rules.get(i).path("ruleKey").asString(), v));
            expected = expected.add(BigDecimal.valueOf((long) v * rules.get(i).path("weight").asInt()));
        }
        expected = expected.divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
        assertThat(call(put("/api/scores/" + sid + "/grade"), mgr.token(), "{\"items\":[" + items.substring(0, items.indexOf("}") + 1) + "]}")
                .path("message").asString()).satisfiesAnyOf(m -> assertThat(m).contains("请填写"), m -> assertThat(rules.size()).isEqualTo(1));
        assertThat(raw(put("/api/scores/" + sid + "/grade"), mem.token(), "{\"items\":[" + items + "]}").getResponse().getStatus()).isEqualTo(403);
        JsonNode graded = call(put("/api/scores/" + sid + "/grade"), mgr.token(), "{\"items\":[" + items + "],\"comment\":\"按期完成\"}").path("data");
        assertThat(graded.path("status").asString()).isEqualTo("SCORED");
        assertThat(graded.path("total").decimalValue()).isEqualByComparingTo(expected);
        assertThat(graded.path("scorerName").asString()).isEqualTo("考评经理");

        // 部门负责人不能发布；公司领导发布
        assertThat(raw(post("/api/scores/" + sid + "/publish"), mgr.token(), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(get("/api/scores/todo"), boss.token(), null).path("data").path("toPublish").asInt()).isPositive();
        assertThat(call(post("/api/scores/" + sid + "/publish"), boss.token(), null).path("code").asInt()).isZero();
        assertThat(call(put("/api/scores/" + sid + "/grade"), mgr.token(), "{\"items\":[" + items + "]}").path("message").asString()).contains("已发布");

        memView = call(get("/api/scores/" + sid), mem.token(), null).path("data");
        assertThat(memView.path("detailVisible").asBoolean()).isTrue();
        assertThat(memView.path("total").decimalValue()).isEqualByComparingTo(expected);
        assertThat(call(get("/api/projects/" + pid), mem.token(), null).path("data").path("score").path("total").decimalValue())
                .isEqualByComparingTo(expected);

        // 年度汇总：部门负责人看到本部门，成员只看到自己
        int year = LocalDate.now().getYear();
        int mi = LocalDate.now().getMonthValue() - 1;
        JsonNode annual = call(get("/api/annual").param("year", String.valueOf(year)), mgr.token(), null).path("data");
        assertThat(annual.path("scope").asString()).isEqualTo("本部门");
        assertThat(annual.path("tiles").path("archivedProjects").asInt()).isPositive();
        JsonNode memRow = null;
        for (JsonNode r : annual.path("persons")) {
            if (r.path("userId").asLong() == mem.id()) {
                memRow = r;
            }
        }
        assertThat(memRow).isNotNull();
        assertThat(memRow.path("projects").asInt()).isEqualTo(1);
        assertThat(memRow.path("monthly").get(mi).decimalValue()).isEqualByComparingTo(expected);
        assertThat(memRow.path("hours").asDouble()).isEqualTo(2.0);
        assertThat(annual.toString()).doesNotContain("考评外人");

        JsonNode own = call(get("/api/annual"), mem.token(), null).path("data");
        assertThat(own.path("persons").size()).isEqualTo(1);
        assertThat(own.path("me").path("rank").asInt()).isEqualTo(1);

        // 撤回发布后，成员又看不到得分
        assertThat(call(post("/api/scores/" + sid + "/unpublish"), boss.token(), "{\"reason\":\"评语需补充\"}").path("code").asInt()).isZero();
        memView = call(get("/api/scores/" + sid), mem.token(), null).path("data");
        assertThat(memView.path("status").asString()).isEqualTo("SCORED");
        assertThat(memView.path("detailVisible").asBoolean()).isFalse();
        // 批量发布
        assertThat(call(post("/api/scores/publish").param("month", MONTH), boss.token(), null).path("data").asInt()).isPositive();
    }

    @Test
    void monthFormatIsValidated() throws Exception {
        assertThat(call(get("/api/scores").param("month", "2026-13"), adminToken(), null).path("message").asString()).contains("YYYY-MM");
    }
}
