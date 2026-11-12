package com.hongyu.water.system.dept;

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
import com.hongyu.water.system.dept.DeptDtos.DeptForm;
import com.hongyu.water.system.dept.DeptDtos.DeptNode;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/system/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    /** 部门树：部门管理、用户管理（筛选 / 选择部门）都会用到。 */
    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('system:dept', 'system:user')")
    public ApiResponse<List<DeptNode>> tree() {
        return ApiResponse.ok(deptService.tree());
    }

    @OperLog(module = "部门管理", action = "新增部门")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public ApiResponse<Long> create(@Valid @RequestBody DeptForm form) {
        return ApiResponse.ok(deptService.create(form));
    }

    @OperLog(module = "部门管理", action = "编辑部门")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody DeptForm form) {
        deptService.update(id, form);
        return ApiResponse.ok();
    }

    @OperLog(module = "部门管理", action = "删除部门")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.ok();
    }
}
