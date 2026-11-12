package com.hongyu.water.system.user;

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
import com.hongyu.water.system.log.OperLog;
import com.hongyu.water.system.user.UserDtos.ResetPasswordResult;
import com.hongyu.water.system.user.UserDtos.StatusRequest;
import com.hongyu.water.system.user.UserDtos.UserForm;
import com.hongyu.water.system.user.UserDtos.UserOption;
import com.hongyu.water.system.user.UserDtos.UserQuery;
import com.hongyu.water.system.user.UserDtos.UserVO;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/system/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<PageResult<UserVO>> page(UserQuery q) {
        return ApiResponse.ok(userService.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user')")
    public ApiResponse<UserVO> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    /** 人员下拉选项（如选择部门负责人）。 */
    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('system:user', 'system:dept')")
    public ApiResponse<List<UserOption>> options() {
        return ApiResponse.ok(userService.options());
    }

    @OperLog(module = "用户管理", action = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public ApiResponse<Long> create(@Valid @RequestBody UserForm form) {
        return ApiResponse.ok(userService.create(form));
    }

    @OperLog(module = "用户管理", action = "编辑用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserForm form) {
        userService.update(id, form);
        return ApiResponse.ok();
    }

    @OperLog(module = "用户管理", action = "启用 / 停用用户")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status')")
    public ApiResponse<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        userService.changeStatus(id, req.status());
        return ApiResponse.ok();
    }

    @OperLog(module = "用户管理", action = "解除锁定")
    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('system:user:status')")
    public ApiResponse<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return ApiResponse.ok();
    }

    @OperLog(module = "用户管理", action = "重置密码")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset-pwd')")
    public ApiResponse<ResetPasswordResult> resetPassword(@PathVariable Long id) {
        return ApiResponse.ok(userService.resetPassword(id));
    }

    @OperLog(module = "用户管理", action = "删除用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }
}
