package com.hongyu.water.doc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.hongyu.water.IntegrationTestBase;

import tools.jackson.databind.JsonNode;

/**
 * 资料中心：上传、查询、预览 / 下载凭证、下载审批（含涉密两级审批）、网盘同步。
 */
class DocIntegrationTest extends IntegrationTestBase {

    private long categoryId(String token, String parent, String child) throws Exception {
        for (JsonNode p : call(get("/api/doc-categories"), token, null).path("data")) {
            if (p.path("name").asString().equals(parent)) {
                if (child == null) {
                    return p.path("id").asLong();
                }
                for (JsonNode c : p.path("children")) {
                    if (c.path("name").asString().equals(child)) {
                        return c.path("id").asLong();
                    }
                }
            }
        }
        throw new IllegalStateException("category not found");
    }

    private long upload(String token, String fileName, byte[] content, String json) throws Exception {
        var req = multipart("/api/docs")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                .file(new MockMultipartFile("data", "", "application/json", json.getBytes(StandardCharsets.UTF_8)));
        JsonNode r = call(req, token, null);
        assertThat(r.path("code").asInt()).as(r.toString()).isZero();
        return r.path("data").asLong();
    }

    /** 获取凭证并读取文件 */
    private MockHttpServletResponse fetch(String token, long docId, String mode, Long requestId) throws Exception {
        var req = post("/api/docs/" + docId + "/ticket").param("mode", mode);
        if (requestId != null) {
            req.param("requestId", String.valueOf(requestId));
        }
        JsonNode t = call(req, token, null);
        assertThat(t.path("code").asInt()).as(t.toString()).isZero();
        return raw(get(t.path("data").path("url").asString()), null, null).getResponse();
    }

    @Test
    void uploadSearchPreviewAndFreeDownload() throws Exception {
        String admin = adminToken();
        TestUser docAdmin = activeUser(admin, "资料管理员甲", 4, "staff", "docadmin");
        TestUser staff = activeUser(admin, "查资料员工", 2, "staff");
        long cat = categoryId(docAdmin.token(), "相关规范", "水利行业标准");
        byte[] pdf = "%PDF-1.4 test content 防洪标准".getBytes(StandardCharsets.UTF_8);

        // 普通员工不能上传
        assertThat(raw(multipart("/api/docs").file(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"categoryId\":" + cat + ",\"name\":\"x\",\"level\":\"PUBLIC\"}").getBytes())), staff.token(), null)
                .getResponse().getStatus()).isEqualTo(403);
        // 不支持的类型
        assertThat(call(multipart("/api/docs").file(new MockMultipartFile("file", "a.exe", "x", pdf))
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"categoryId\":" + cat + ",\"name\":\"x\",\"level\":\"PUBLIC\"}").getBytes())), docAdmin.token(), null)
                .path("message").asString()).contains("不支持的文件类型");

        long id = upload(docAdmin.token(), "GB 50201-2014 防洪标准.pdf", pdf, """
                {"categoryId":%d,"name":"防洪标准（测试）","code":"GB 50201-2014","year":2014,"level":"PUBLIC","needApproval":false}""".formatted(cat));

        JsonNode list = call(get("/api/docs").param("keyword", "50201"), staff.token(), null).path("data");
        assertThat(list.path("total").asLong()).isEqualTo(1);
        JsonNode doc = list.path("records").get(0);
        assertThat(doc.path("categoryPath").asString()).isEqualTo("相关规范 / 水利行业标准");
        assertThat(doc.path("previewable").asBoolean()).isTrue();
        assertThat(doc.path("isNew").asBoolean()).isTrue();
        assertThat(doc.path("access").path("free").asBoolean()).isTrue();

        // 预览：内联返回；下载：附件
        MockHttpServletResponse view = fetch(staff.token(), id, "preview", null);
        assertThat(view.getStatus()).isEqualTo(200);
        assertThat(view.getContentAsByteArray()).isEqualTo(pdf);
        assertThat(view.getHeader("Content-Disposition")).startsWith("inline");
        // 预览通过同源 iframe 加载，不能是 DENY
        assertThat(view.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        MockHttpServletResponse dl = fetch(staff.token(), id, "download", null);
        assertThat(dl.getHeader("Content-Disposition")).startsWith("attachment").contains("UTF-8''");
        assertThat(dl.getContentAsByteArray()).isEqualTo(pdf);

        JsonNode after = call(get("/api/docs/" + id), staff.token(), null).path("data");
        assertThat(after.path("viewCount").asInt()).isEqualTo(1);
        assertThat(after.path("downloadCount").asInt()).isEqualTo(1);
        JsonNode logs = call(get("/api/docs/logs").param("docId", String.valueOf(id)), docAdmin.token(), null).path("data");
        assertThat(logs.toString()).contains("\"VIEW\"", "\"DOWNLOAD\"", "\"UPLOAD\"", "查资料员工");

        // 无效凭证
        assertThat(raw(get("/api/files/not-a-ticket"), null, null).getResponse().getStatus()).isEqualTo(410);
        // 下架后普通员工搜不到
        call(put("/api/docs/" + id + "/status"), docAdmin.token(), "{\"status\":\"OFF\"}");
        assertThat(call(get("/api/docs").param("keyword", "50201"), staff.token(), null).path("data").path("total").asLong()).isZero();
        assertThat(call(get("/api/docs").param("keyword", "50201").param("manage", "true"), docAdmin.token(), null)
                .path("data").path("total").asLong()).isEqualTo(1);
    }

    @Test
    void downloadApprovalWithSecretDocs() throws Exception {
        String admin = adminToken();
        TestUser docAdmin = activeUser(admin, "资料管理员乙", 4, "staff", "docadmin");
        TestUser staff = activeUser(admin, "申请员工", 2, "staff");
        TestUser mgr = activeUser(admin, "一部审批人", 2, "staff", "manager");
        TestUser mgr3 = activeUser(admin, "水工审批人", 3, "staff", "manager");
        TestUser boss = activeUser(admin, "终审领导", 1, "staff", "leader");
        long cat = categoryId(docAdmin.token(), "公司成果", "设计报告");
        long internal = upload(docAdmin.token(), "初设报告.docx", "docx-bytes".getBytes(), """
                {"categoryId":%d,"name":"南渠堤防初步设计报告","level":"INTERNAL"}""".formatted(cat));
        long secret = upload(docAdmin.token(), "施工图.dwg", "dwg-bytes".getBytes(), """
                {"categoryId":%d,"name":"南渠堤防施工图","level":"SECRET","needApproval":false}""".formatted(cat));
        // 涉密资料即使选择免审也必须审批
        assertThat(call(get("/api/docs/" + secret), staff.token(), null).path("data").path("needApproval").asBoolean()).isTrue();

        // 未审批不能下载；不能预览不支持的格式
        assertThat(call(post("/api/docs/" + internal + "/ticket").param("mode", "download"), staff.token(), null).path("code").asInt()).isEqualTo(403);
        assertThat(call(post("/api/docs/" + internal + "/ticket").param("mode", "preview"), staff.token(), null)
                .path("message").asString()).contains("暂不支持在线预览");

        JsonNode req = call(post("/api/doc-requests"), staff.token(), """
                {"docIds":[%d,%d],"purpose":"青溪县项目设计参考"}""".formatted(internal, secret)).path("data");
        long rid = req.path("id").asLong();
        assertThat(req.path("secret").asBoolean()).isTrue();
        assertThat(req.path("steps").size()).isEqualTo(2);
        assertThat(call(post("/api/doc-requests"), staff.token(), "{\"docIds\":[%d],\"purpose\":\"再申请\"}".formatted(internal))
                .path("message").asString()).contains("审批中");
        assertThat(call(get("/api/docs/" + internal), staff.token(), null).path("data").path("access").path("pending").asBoolean()).isTrue();

        // 部门负责人可见、其他部门负责人不可见
        assertThat(call(get("/api/doc-requests/todo"), mgr.token(), null).toString()).contains("青溪县项目设计参考");
        assertThat(call(get("/api/doc-requests/todo"), mgr3.token(), null).toString()).doesNotContain("青溪县项目设计参考");
        assertThat(call(post("/api/doc-requests/" + rid + "/approve"), mgr3.token(), "{}").path("code").asInt()).isEqualTo(403);

        // 第一步部门负责人通过 → 第二步领导终审
        call(post("/api/doc-requests/" + rid + "/approve"), mgr.token(), "{\"comment\":\"同意\"}");
        assertThat(call(post("/api/doc-requests/" + rid + "/approve"), mgr.token(), "{}").path("message").asString()).contains("公司领导终审");
        assertThat(call(get("/api/doc-requests/todo-count"), boss.token(), null).path("data").asInt()).isPositive();
        call(post("/api/doc-requests/" + rid + "/approve"), boss.token(), "{}");

        JsonNode mine = call(get("/api/doc-requests/mine"), staff.token(), null).path("data").path("records").get(0);
        assertThat(mine.path("status").asString()).isEqualTo("APPROVED");
        assertThat(mine.path("actions").path("download").asBoolean()).isTrue();
        assertThat(mine.path("steps").get(1).path("approverName").asString()).isEqualTo("终审领导");

        // 下载次数限制（默认 3 次）
        for (int i = 0; i < 3; i++) {
            assertThat(fetch(staff.token(), secret, "download", rid).getContentAsString()).isEqualTo("dwg-bytes");
        }
        assertThat(call(post("/api/docs/" + secret + "/ticket").param("mode", "download"), staff.token(), null)
                .path("message").asString()).contains("次数");
        JsonNode access = call(get("/api/docs/" + internal), staff.token(), null).path("data").path("access");
        assertThat(access.path("canDownload").asBoolean()).isTrue();
        assertThat(access.path("remaining").asInt()).isEqualTo(3);

        // 驳回、撤回
        JsonNode r2 = call(post("/api/doc-requests"), mgr.token(), "{\"docIds\":[%d],\"purpose\":\"经理自己申请\"}".formatted(internal)).path("data");
        assertThat(call(get("/api/doc-requests/todo"), mgr.token(), null).toString()).doesNotContain("经理自己申请");
        assertThat(call(post("/api/doc-requests/" + r2.path("id").asLong() + "/reject"), boss.token(), "{\"comment\":\"\"}")
                .path("message").asString()).contains("驳回原因");
        call(post("/api/doc-requests/" + r2.path("id").asLong() + "/reject"), boss.token(), "{\"comment\":\"请在线预览\"}");
        JsonNode r3 = call(post("/api/doc-requests"), mgr.token(), "{\"docIds\":[%d],\"purpose\":\"再申请一次\"}".formatted(internal)).path("data");
        assertThat(call(post("/api/doc-requests/" + r3.path("id").asLong() + "/cancel"), staff.token(), null).path("code").asInt()).isEqualTo(403);
        assertThat(call(post("/api/doc-requests/" + r3.path("id").asLong() + "/cancel"), mgr.token(), null).path("code").asInt()).isZero();
        JsonNode mgrMine = call(get("/api/doc-requests/mine"), mgr.token(), null).path("data").path("records");
        assertThat(mgrMine.toString()).contains("CANCELLED", "REJECTED", "请在线预览");
    }

    @Test
    void nasSync() throws Exception {
        String admin = adminToken();
        TestUser docAdmin = activeUser(admin, "资料管理员丙", 4, "staff", "docadmin");
        Path std = NAS_DIR.resolve("相关规范/水利行业标准/SL 252-2017 水利水电工程等级划分及洪水标准.pdf");
        Path report = NAS_DIR.resolve("公司成果/设计报告/2025/南渠堤防初设报告.docx");
        Path root = NAS_DIR.resolve("网盘说明.txt");
        Path temp = NAS_DIR.resolve("公司成果/~$临时文件.docx");
        for (Path p : new Path[] {std, report, root, temp}) {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "content of " + p.getFileName());
        }

        JsonNode info = call(get("/api/docs/storage"), docAdmin.token(), null).path("data");
        assertThat(info.path("nasEnabled").asBoolean()).isTrue();
        assertThat(info.path("nasType").asString()).isEqualTo("mount");

        JsonNode r = call(post("/api/docs/sync"), docAdmin.token(), null).path("data");
        assertThat(r.path("scanned").asInt()).isEqualTo(3);
        assertThat(r.path("added").asInt()).isEqualTo(3);

        JsonNode found = call(get("/api/docs").param("keyword", "SL 252").param("manage", "true"), docAdmin.token(), null)
                .path("data").path("records").get(0);
        assertThat(found.path("code").asString()).isEqualTo("SL 252-2017");
        assertThat(found.path("year").asInt()).isEqualTo(2017);
        assertThat(found.path("name").asString()).isEqualTo("水利水电工程等级划分及洪水标准");
        assertThat(found.path("storageType").asString()).isEqualTo("NAS");
        assertThat(found.path("categoryPath").asString()).isEqualTo("相关规范 / 水利行业标准");
        long nasId = found.path("id").asLong();
        assertThat(call(get("/api/docs").param("keyword", "南渠堤防初设").param("manage", "true"), docAdmin.token(), null)
                .path("data").path("records").get(0).path("categoryPath").asString()).isEqualTo("公司成果 / 设计报告");
        assertThat(call(get("/api/docs").param("keyword", "网盘说明").param("manage", "true"), docAdmin.token(), null)
                .path("data").path("records").get(0).path("categoryPath").asString()).isEqualTo("未分类");

        // 预览网盘文件
        assertThat(fetch(docAdmin.token(), nasId, "preview", null).getContentAsString()).contains("SL 252-2017");

        // 网盘中删除 → 自动下架；恢复 → 自动上架；修改 → 更新
        Files.delete(std);
        Files.writeString(report, "updated, longer content");
        r = call(post("/api/docs/sync"), docAdmin.token(), null).path("data");
        assertThat(r.path("missing").asInt()).isEqualTo(1);
        assertThat(r.path("updated").asInt()).isEqualTo(1);
        JsonNode missing = call(get("/api/docs/" + nasId), docAdmin.token(), null).path("data");
        assertThat(missing.path("status").asString()).isEqualTo("OFF");
        assertThat(missing.path("sourceMissing").asBoolean()).isTrue();
        assertThat(call(put("/api/docs/" + nasId + "/status"), docAdmin.token(), "{\"status\":\"ON\"}").path("message").asString()).contains("源文件");
        Files.writeString(std, "back again");
        r = call(post("/api/docs/sync"), docAdmin.token(), null).path("data");
        assertThat(r.path("restored").asInt()).isEqualTo(1);
        // 恢复后可以重新预览
        assertThat(fetch(docAdmin.token(), nasId, "preview", null).getContentAsString()).isEqualTo("back again");
        // 网盘资料不能在系统中替换文件
        assertThat(call(multipart("/api/docs/" + nasId + "/file").file(new MockMultipartFile("file", "x.pdf", "application/pdf", "x".getBytes())),
                docAdmin.token(), null).path("message").asString()).contains("网盘");
    }

    @Test
    void categoryRules() throws Exception {
        String admin = adminToken();
        long top = categoryId(admin, "行业资料", null);
        long child = categoryId(admin, "行业资料", "政策文件");
        assertThat(call(post("/api/doc-categories"), admin, "{\"parentId\":%d,\"name\":\"三级\"}".formatted(child))
                .path("message").asString()).contains("最多两级");
        assertThat(call(post("/api/doc-categories"), admin, "{\"parentId\":%d,\"name\":\"政策文件\"}".formatted(top))
                .path("message").asString()).contains("已存在");
        assertThat(call(delete("/api/doc-categories/" + top), admin, null).path("message").asString()).contains("下级分类");
        long id = call(post("/api/doc-categories"), admin, "{\"parentId\":%d,\"name\":\"测试分类\"}".formatted(top)).path("data").asLong();
        assertThat(call(delete("/api/doc-categories/" + id), admin, null).path("code").asInt()).isZero();
        // 系统内部参数不能通过接口修改
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"doc.nas.last-sync\",\"value\":\"x\"}]")
                .path("message").asString()).contains("参数不存在");
        assertThat(call(put("/api/system/configs"), admin, "[{\"key\":\"doc.download.max-times\",\"value\":\"0\"}]")
                .path("message").asString()).contains("1~20");
    }
}
