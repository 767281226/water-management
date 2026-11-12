package com.hongyu.water.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.hongyu.water.IntegrationTestBase;
import com.hongyu.water.weekly.WeekRules;

import tools.jackson.databind.JsonNode;

/**
 * 项目台账与周报的集成测试。部门：2 规划设计一部，3 水工设计部。
 */
class ProjectWeeklyIntegrationTest extends IntegrationTestBase {

    private static final LocalDate MONDAY = WeekRules.mondayOf(LocalDate.now());

    private long createProject(String token, String name, long leaderId, long deptId, String members) throws Exception {
        JsonNode r = call(post("/api/projects"), token, """
                {"name":"%s","type":"设计","client":"测试水利局","leaderId":%d,"deptId":%d,"contractAmount":88.5,
                 "startDate":"2026-01-01","planEndDate":"2026-12-31","memberIds":[%s]}""".formatted(name, leaderId, deptId, members));
        assertThat(r.path("code").asInt()).as(r.toString()).isZero();
        return r.path("data").asLong();
    }

    @Test
    void projectVisibilityAndPermissions() throws Exception {
        String admin = adminToken();
        TestUser mgr = activeUser(admin, "一部经理", 2, "staff", "manager");
        TestUser leader = activeUser(admin, "项目负责人", 2, "staff");
        TestUser member = activeUser(admin, "水工成员", 3, "staff");
        TestUser outsider = activeUser(admin, "无关员工", 3, "staff");
        TestUser mgr3 = activeUser(admin, "水工经理", 3, "staff", "manager");

        // 普通员工不能新建项目；部门负责人可以
        assertThat(raw(post("/api/projects"), leader.token(), "{\"name\":\"x\",\"type\":\"设计\",\"leaderId\":1,\"planEndDate\":\"2026-12-31\"}")
                .getResponse().getStatus()).isEqualTo(403);
        long id = createProject(mgr.token(), "可见性测试项目", leader.id(), 2, String.valueOf(member.id()));
        JsonNode created = call(get("/api/projects/" + id), mgr.token(), null).path("data").path("project");
        assertThat(created.path("code").asString()).matches("HY-\\d{4}-\\d{3}");
        assertThat(created.path("statusLabel").asString()).isEqualTo("未开始");

        // 部门负责人不能在别的部门建项目
        assertThat(call(post("/api/projects"), mgr.token(), """
                {"name":"越权","type":"设计","leaderId":%d,"deptId":3,"planEndDate":"2026-12-31"}""".formatted(outsider.id()))
                .path("code").asInt()).isEqualTo(403);
        assertThat(call(post("/api/projects"), mgr.token(), """
                {"name":"类型错误","type":"养殖","leaderId":%d,"deptId":2,"planEndDate":"2026-12-31"}""".formatted(leader.id()))
                .path("message").asString()).contains("项目类型不正确");

        // 可见性：成员可见、无关人员不可见、其他部门负责人不可见
        assertThat(call(get("/api/projects").param("keyword", "可见性测试"), member.token(), null).path("data").path("total").asLong()).isEqualTo(1);
        assertThat(call(get("/api/projects").param("keyword", "可见性测试"), outsider.token(), null).path("data").path("total").asLong()).isZero();
        assertThat(call(get("/api/projects/" + id), mgr3.token(), null).path("code").asInt()).isEqualTo(403);

        // 合同金额：普通成员看不到，负责人和部门负责人可以看到
        assertThat(call(get("/api/projects/" + id), member.token(), null).path("data").path("project").path("contractAmount").isNull()).isTrue();
        assertThat(call(get("/api/projects/" + id), leader.token(), null).path("data").path("project").path("contractAmount").asDouble()).isEqualTo(88.5);

        // 编辑：负责人可以，成员不可以
        String edit = """
                {"name":"可见性测试项目（改）","type":"规划","leaderId":%d,"deptId":2,"planEndDate":"2026-12-31","memberIds":[%d]}"""
                .formatted(leader.id(), member.id());
        assertThat(call(put("/api/projects/" + id), leader.token(), edit).path("code").asInt()).isZero();
        assertThat(call(put("/api/projects/" + id), member.token(), edit).path("code").asInt()).isEqualTo(403);
        JsonNode actions = call(get("/api/projects/" + id), member.token(), null).path("data").path("project").path("actions");
        assertThat(actions.path("edit").asBoolean()).isFalse();
    }

    @Test
    void archiveFlow() throws Exception {
        String admin = adminToken();
        TestUser mgr = activeUser(admin, "存档经理", 2, "staff", "manager");
        TestUser leader = activeUser(admin, "存档负责人", 2, "staff");
        long id = createProject(mgr.token(), "存档流程项目", leader.id(), 2, "");

        assertThat(call(post("/api/projects/" + id + "/archive/apply"), leader.token(), null).path("message").asString()).contains("完成后");
        assertThat(call(put("/api/projects/" + id + "/status"), leader.token(), "{\"status\":\"ARCHIVED\"}").path("message").asString()).contains("不能改为");
        call(put("/api/projects/" + id + "/status"), leader.token(), "{\"status\":\"IN_PROGRESS\"}");
        call(put("/api/projects/" + id + "/status"), leader.token(), "{\"status\":\"COMPLETED\"}");
        JsonNode p = call(get("/api/projects/" + id), leader.token(), null).path("data").path("project");
        assertThat(p.path("actualEndDate").asString()).isEqualTo(LocalDate.now().toString());
        assertThat(p.path("actions").path("applyArchive").asBoolean()).isTrue();

        // 申请 → 退回 → 再申请 → 确认
        assertThat(call(post("/api/projects/" + id + "/archive/apply"), mgr.token(), null).path("code").asInt()).isEqualTo(403);
        call(post("/api/projects/" + id + "/archive/apply"), leader.token(), null);
        assertThat(raw(post("/api/projects/" + id + "/archive/confirm"), leader.token(), null).getResponse().getStatus()).isEqualTo(403);
        call(post("/api/projects/" + id + "/archive/reject"), mgr.token(), "{\"reason\":\"成果资料未归档\"}");
        p = call(get("/api/projects/" + id), leader.token(), null).path("data").path("project");
        assertThat(p.path("archiveApplied").asBoolean()).isFalse();
        assertThat(p.path("archiveRejectReason").asString()).isEqualTo("成果资料未归档");

        call(post("/api/projects/" + id + "/archive/apply"), leader.token(), null);
        assertThat(call(post("/api/projects/" + id + "/archive/confirm"), mgr.token(), null).path("code").asInt()).isZero();
        p = call(get("/api/projects/" + id), leader.token(), null).path("data").path("project");
        assertThat(p.path("status").asString()).isEqualTo("ARCHIVED");
        assertThat(p.path("archivedByName").asString()).isEqualTo("存档经理");
        // 存档后不能修改
        assertThat(call(put("/api/projects/" + id), leader.token(), """
                {"name":"改","type":"设计","leaderId":%d,"deptId":2,"planEndDate":"2026-12-31"}""".formatted(leader.id()))
                .path("message").asString()).contains("已存档");
    }

    @Test
    void weeklyPlanReportStatAndReturn() throws Exception {
        String admin = adminToken();
        TestUser mgr = activeUser(admin, "周报经理", 2, "staff", "manager");
        TestUser staff = activeUser(admin, "周报员工", 2, "staff");
        TestUser other = activeUser(admin, "外部门员工", 3, "staff");
        long project = createProject(mgr.token(), "周报测试项目", mgr.id(), 2, String.valueOf(staff.id()));
        long notMine = createProject(mgr.token(), "非成员项目", mgr.id(), 2, "");

        // 可选项目只包含自己参与的
        JsonNode options = call(get("/api/projects/options/fillable"), staff.token(), null).path("data");
        assertThat(options.toString()).contains("周报测试项目").doesNotContain("非成员项目");

        String week = MONDAY.toString();
        assertThat(call(put("/api/weekly/plan"), staff.token(), """
                {"weekStart":"%s","items":[{"projectId":%d,"content":"偷填","planDays":1}]}""".formatted(week, notMine))
                .path("message").asString()).contains("不是项目");
        assertThat(call(put("/api/weekly/plan"), staff.token(), """
                {"weekStart":"%s","items":[{"content":"x","planDays":1}]}""".formatted(MONDAY.plusWeeks(2)))
                .path("message").asString()).contains("下周");
        // 未提交计划不能填完成情况
        assertThat(call(get("/api/weekly/mine").param("weekStart", week), staff.token(), null)
                .path("data").path("canSubmitReport").asBoolean()).isFalse();

        JsonNode plan = call(put("/api/weekly/plan"), staff.token(), """
                {"weekStart":"%s","items":[
                  {"projectId":%d,"content":"水文分析计算","target":"完成计算书","planDays":2},
                  {"content":"部门例会","planDays":0.5}]}""".formatted(week, project)).path("data");
        assertThat(plan.path("planStatus").asString()).isEqualTo("SUBMITTED");
        assertThat(plan.path("planDays").asDouble()).isEqualTo(2.5);
        long item1 = plan.path("items").get(0).path("id").asLong();
        long item2 = plan.path("items").get(1).path("id").asLong();

        String noReason = """
                {"weekStart":"%s","items":[{"id":%d,"completion":50},{"id":%d,"completion":100}]}""".formatted(week, item1, item2);
        assertThat(call(put("/api/weekly/report"), staff.token(), noReason).path("message").asString()).contains("未完成原因");
        JsonNode report = call(put("/api/weekly/report"), staff.token(), """
                {"weekStart":"%s","items":[{"id":%d,"completion":50,"actualDays":2,"reason":"资料延迟"},{"id":%d,"completion":100}],
                 "extras":[{"projectId":%d,"content":"配合投标","actualDays":0.5}]}""".formatted(week, item1, item2, project)).path("data");
        assertThat(report.path("reportStatus").asString()).isEqualTo("SUBMITTED");
        assertThat(report.path("completionRate").asInt()).isEqualTo(75);
        assertThat(report.path("items").size()).isEqualTo(3);

        // 完成情况提交后计划锁定
        assertThat(call(put("/api/weekly/plan"), staff.token(), """
                {"weekStart":"%s","items":[{"content":"改","planDays":1}]}""".formatted(week)).path("message").asString()).contains("锁定");
        // 下周可带入未完成事项（下周查看时"上周"即本周）
        JsonNode next = call(get("/api/weekly/mine").param("weekStart", MONDAY.plusWeeks(1).toString()), staff.token(), null).path("data");
        assertThat(next.path("lastWeekUnfinished").size()).isEqualTo(1);
        assertThat(next.path("lastWeekUnfinished").get(0).path("content").asString()).isEqualTo("水文分析计算");

        // 部门统计：只包含本部门人员
        JsonNode stat = call(get("/api/weekly/stat").param("weekStart", week), mgr.token(), null).path("data");
        assertThat(stat.toString()).contains("周报员工").doesNotContain("外部门员工");
        JsonNode row = null;
        for (JsonNode r : stat.path("rows")) {
            if (r.path("userId").asLong() == staff.id()) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.path("completionRate").asInt()).isEqualTo(75);
        long planId = row.path("planId").asLong();

        // 普通员工不能看别人的统计 / 周报，也不能点评
        assertThat(raw(get("/api/weekly/stat"), other.token(), null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(get("/api/weekly/stat"), other.token(), null).path("data").path("total").asInt()).isEqualTo(1);
        assertThat(call(get("/api/weekly/plans/" + planId), other.token(), null).path("code").asInt()).isEqualTo(403);
        assertThat(raw(put("/api/weekly/plans/" + planId + "/comment"), other.token(), "{\"comment\":\"x\"}").getResponse().getStatus()).isEqualTo(403);

        // 点评、退回后员工可以重新填写
        call(put("/api/weekly/plans/" + planId + "/comment"), mgr.token(), "{\"comment\":\"注意评审节点\"}");
        call(post("/api/weekly/plans/" + planId + "/return"), mgr.token(), "{\"reason\":\"工时填写不准确\"}");
        JsonNode mine = call(get("/api/weekly/mine").param("weekStart", week), staff.token(), null).path("data");
        assertThat(mine.path("plan").path("planStatus").asString()).isEqualTo("RETURNED");
        assertThat(mine.path("plan").path("comment").asString()).isEqualTo("注意评审节点");
        assertThat(mine.path("plan").path("commentByName").asString()).isEqualTo("周报经理");
        assertThat(mine.path("canEditPlan").asBoolean()).isTrue();
        JsonNode todo = call(get("/api/weekly/todo"), staff.token(), null).path("data");
        assertThat(todo.path("planStatus").asString()).isEqualTo("RETURNED");
        assertThat(todo.path("returnReason").asString()).isEqualTo("工时填写不准确");

        // 项目详情显示周报动态；有周报记录的项目不能删除
        JsonNode detail = call(get("/api/projects/" + project), mgr.token(), null).path("data");
        assertThat(detail.path("weeklyCount").asLong()).isEqualTo(2);
        assertThat(call(delete("/api/projects/" + project), mgr.token(), null).path("message").asString()).contains("周报记录");
        assertThat(call(delete("/api/projects/" + notMine), mgr.token(), null).path("code").asInt()).isZero();

        JsonNode history = call(get("/api/weekly/mine/history"), staff.token(), null).path("data");
        assertThat(history.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void deadlineParsing() {
        LocalDate mon = LocalDate.of(2026, 11, 9);
        assertThat(WeekRules.deadline(mon, "周一 23:59", 0, LocalTime.NOON)).isEqualTo(mon.atTime(23, 59));
        assertThat(WeekRules.deadline(mon, "周五 18:00", 0, LocalTime.NOON)).isEqualTo(mon.plusDays(4).atTime(18, 0));
        assertThat(WeekRules.deadline(mon, "下周一 12:00", 0, LocalTime.NOON)).isEqualTo(mon.plusDays(7).atTime(12, 0));
        assertThat(WeekRules.deadline(mon, "周日 9：30", 0, LocalTime.NOON)).isEqualTo(mon.plusDays(6).atTime(9, 30));
        // 格式错误时使用默认值
        assertThat(WeekRules.deadline(mon, "每周一", 4, LocalTime.of(23, 59))).isEqualTo(mon.plusDays(4).atTime(23, 59));
    }

    @Test
    void configValidatesDeadlineFormat() throws Exception {
        String admin = adminToken();
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"fill.plan.deadline\",\"value\":\"每周一晚上\"}]")
                .path("message").asString()).contains("格式");
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"project.types\",\"value\":\" , \"}]")
                .path("message").asString()).contains("项目类型");
        assertThat(call(get("/api/projects/types"), admin, null).path("data").toString()).contains("设计");
    }
}
