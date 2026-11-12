package com.hongyu.water.project;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.project.ProjectDtos.ProjectDetail;
import com.hongyu.water.project.ProjectDtos.ProjectForm;
import com.hongyu.water.project.ProjectDtos.ProjectOption;
import com.hongyu.water.project.ProjectDtos.ProjectQuery;
import com.hongyu.water.project.ProjectDtos.ProjectVO;
import com.hongyu.water.project.ProjectDtos.RejectRequest;
import com.hongyu.water.project.ProjectDtos.StatusRequest;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:ledger')")
    public ApiResponse<PageResult<ProjectVO>> page(ProjectQuery q) {
        return ApiResponse.ok(service.page(q, SecurityUtils.current()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:ledger')")
    public ApiResponse<ProjectDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id, SecurityUtils.current()));
    }

    /** 项目类型（参数配置 › 项目设置） */
    @GetMapping("/types")
    public ApiResponse<List<String>> types() {
        return ApiResponse.ok(service.types());
    }

    /** 填写周报时可选的项目 */
    @GetMapping("/options/fillable")
    @PreAuthorize("hasAnyAuthority('weekly:plan', 'weekly:report')")
    public ApiResponse<List<ProjectOption>> fillable() {
        return ApiResponse.ok(service.fillableOptions(SecurityUtils.current()));
    }

    @OperLog(module = "项目台账", action = "新建项目")
    @PostMapping
    @PreAuthorize("hasAuthority('project:ledger:edit')")
    public ApiResponse<Long> create(@Valid @RequestBody ProjectForm form) {
        return ApiResponse.ok(service.create(form, SecurityUtils.current()));
    }

    /** 项目负责人也可以编辑自己的项目，具体权限在 service 中判断。 */
    @OperLog(module = "项目台账", action = "编辑项目")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:ledger')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectForm form) {
        service.update(id, form, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "项目台账", action = "修改项目状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('project:ledger')")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        service.changeStatus(id, req.status(), SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "项目台账", action = "申请存档")
    @PostMapping("/{id}/archive/apply")
    @PreAuthorize("hasAuthority('project:ledger')")
    public ApiResponse<Void> applyArchive(@PathVariable Long id) {
        service.applyArchive(id, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "项目台账", action = "确认存档")
    @PostMapping("/{id}/archive/confirm")
    @PreAuthorize("hasAuthority('project:ledger:archive')")
    public ApiResponse<Void> confirmArchive(@PathVariable Long id) {
        service.confirmArchive(id, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "项目台账", action = "退回存档申请")
    @PostMapping("/{id}/archive/reject")
    @PreAuthorize("hasAuthority('project:ledger:archive')")
    public ApiResponse<Void> rejectArchive(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        service.rejectArchive(id, req.reason(), SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "项目台账", action = "删除项目")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:ledger:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, SecurityUtils.current());
        return ApiResponse.ok();
    }
}
