package com.hongyu.water.system.role;

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
import com.hongyu.water.system.log.OperLog;
import com.hongyu.water.system.role.RoleDtos.PermissionNode;
import com.hongyu.water.system.role.RoleDtos.RoleForm;
import com.hongyu.water.system.role.RoleDtos.RoleVO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/system")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 角色列表：角色管理、用户管理（分配角色）都会用到。 */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('system:role', 'system:user')")
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/permissions/tree")
    @PreAuthorize("hasAuthority('system:role')")
    public ApiResponse<List<PermissionNode>> permissionTree() {
        return ApiResponse.ok(roleService.permissionTree());
    }

    @OperLog(module = "角色权限", action = "新增角色")
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public ApiResponse<Long> create(@Valid @RequestBody RoleForm form) {
        return ApiResponse.ok(roleService.create(form));
    }

    @OperLog(module = "角色权限", action = "编辑角色")
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody RoleForm form) {
        roleService.update(id, form);
        return ApiResponse.ok();
    }

    @OperLog(module = "角色权限", action = "删除角色")
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
