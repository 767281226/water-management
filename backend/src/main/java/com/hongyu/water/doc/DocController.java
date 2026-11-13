package com.hongyu.water.doc;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.doc.DocDtos.DocForm;
import com.hongyu.water.doc.DocDtos.DocQuery;
import com.hongyu.water.doc.DocDtos.DocVO;
import com.hongyu.water.doc.DocDtos.LogQuery;
import com.hongyu.water.doc.DocDtos.LogVO;
import com.hongyu.water.doc.DocDtos.StatusRequest;
import com.hongyu.water.doc.DocDtos.StorageInfo;
import com.hongyu.water.doc.DocDtos.SyncResult;
import com.hongyu.water.doc.DocDtos.TicketResponse;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/docs")
public class DocController {

    private final DocService docService;
    private final DocFileService fileService;
    private final DocLogService logService;
    private final NasSyncService syncService;

    public DocController(DocService docService, DocFileService fileService, DocLogService logService, NasSyncService syncService) {
        this.docService = docService;
        this.fileService = fileService;
        this.logService = logService;
        this.syncService = syncService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('doc:search', 'doc:manage')")
    public ApiResponse<PageResult<DocVO>> search(DocQuery q) {
        return ApiResponse.ok(docService.search(q, SecurityUtils.current()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('doc:search', 'doc:manage')")
    public ApiResponse<DocVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(docService.detail(id, SecurityUtils.current()));
    }

    /** 获取预览 / 下载链接（mode = preview / download） */
    @PostMapping("/{id}/ticket")
    @PreAuthorize("hasAnyAuthority('doc:search', 'doc:manage')")
    public ApiResponse<TicketResponse> ticket(@PathVariable Long id, @RequestParam(defaultValue = "preview") String mode,
            @RequestParam(required = false) Long requestId) {
        return ApiResponse.ok(fileService.issue(id, mode, requestId, SecurityUtils.current()));
    }

    // ---------------- 资料维护 ----------------

    @OperLog(module = "资料维护", action = "上传资料")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Long> upload(@RequestPart("file") MultipartFile file, @Valid @RequestPart("data") DocForm form) {
        return ApiResponse.ok(docService.upload(file, form, SecurityUtils.current()));
    }

    @OperLog(module = "资料维护", action = "修改资料")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody DocForm form) {
        docService.update(id, form, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "资料维护", action = "更新资料文件")
    @PostMapping(path = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> replace(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        docService.replaceFile(id, file, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "资料维护", action = "上架 / 下架资料")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        docService.setStatus(id, req.status(), SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "资料维护", action = "删除资料")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        docService.delete(id, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<PageResult<LogVO>> logs(LogQuery q) {
        return ApiResponse.ok(logService.page(q));
    }

    // ---------------- 网盘 ----------------

    @GetMapping("/storage")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<StorageInfo> storage() {
        return ApiResponse.ok(syncService.info());
    }

    @OperLog(module = "资料维护", action = "网盘同步")
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<SyncResult> sync() {
        return ApiResponse.ok(syncService.sync(SecurityUtils.current()));
    }
}
