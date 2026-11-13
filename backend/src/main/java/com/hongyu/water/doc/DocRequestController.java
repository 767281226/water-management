package com.hongyu.water.doc;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.doc.DocDtos.ApplyRequest;
import com.hongyu.water.doc.DocDtos.ApproveRequest;
import com.hongyu.water.doc.DocDtos.BatchApproveRequest;
import com.hongyu.water.doc.DocDtos.RejectRequest;
import com.hongyu.water.doc.DocDtos.RequestVO;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/doc-requests")
public class DocRequestController {

    private final DocRequestService service;

    public DocRequestController(DocRequestService service) {
        this.service = service;
    }

    @OperLog(module = "资料中心", action = "申请下载")
    @PostMapping
    @PreAuthorize("hasAuthority('doc:request')")
    public ApiResponse<RequestVO> apply(@Valid @RequestBody ApplyRequest req) {
        return ApiResponse.ok(service.apply(req, SecurityUtils.current()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('doc:request')")
    public ApiResponse<PageResult<RequestVO>> mine(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.mine(SecurityUtils.current(), page, size));
    }

    @OperLog(module = "资料中心", action = "撤回下载申请")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('doc:request')")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        service.cancel(id, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('doc:approve')")
    public ApiResponse<List<RequestVO>> todo() {
        return ApiResponse.ok(service.todo(SecurityUtils.current()));
    }

    /** 工作台待办数量 */
    @GetMapping("/todo-count")
    public ApiResponse<Integer> todoCount() {
        return ApiResponse.ok(service.todoCount(SecurityUtils.current()));
    }

    @GetMapping("/done")
    @PreAuthorize("hasAuthority('doc:approve')")
    public ApiResponse<PageResult<RequestVO>> done(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.done(SecurityUtils.current(), page, size));
    }

    @OperLog(module = "资料中心", action = "审批通过")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('doc:approve')")
    public ApiResponse<Void> approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest req) {
        service.approve(id, req == null ? null : req.comment(), SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "资料中心", action = "审批驳回")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('doc:approve')")
    public ApiResponse<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        service.reject(id, req.comment(), SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "资料中心", action = "批量审批通过")
    @PostMapping("/batch-approve")
    @PreAuthorize("hasAuthority('doc:approve')")
    public ApiResponse<Integer> batchApprove(@Valid @RequestBody BatchApproveRequest req) {
        return ApiResponse.ok(service.batchApprove(req.ids(), SecurityUtils.current()));
    }
}
