package com.hongyu.water.system.user;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserQuery(String keyword, Long deptId, UserStatus status, Long roleId, Integer page, Integer size) {
    }

    public record UserForm(
            @NotBlank(message = "请输入登录账号")
            @Pattern(regexp = "^[A-Za-z0-9_.-]{3,32}$", message = "登录账号为 3~32 位字母、数字、下划线、点或横线")
            String username,
            @NotBlank(message = "请输入工号") @Size(max = 32, message = "工号不超过 32 个字符") String employeeNo,
            @NotBlank(message = "请输入姓名") @Size(max = 32, message = "姓名不超过 32 个字") String realName,
            Long deptId,
            @Size(max = 64, message = "职务不超过 64 个字") String title,
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone,
            @Email(message = "邮箱格式不正确") @Size(max = 128) String email,
            @NotEmpty(message = "请至少选择一个角色") List<Long> roleIds,
            @Size(max = 255, message = "备注不超过 255 个字") String remark) {
    }

    public record StatusRequest(@NotNull(message = "请选择状态") UserStatus status) {
    }

    public record RoleBrief(Long id, String code, String name) {
    }

    public record UserVO(Long id, String username, String employeeNo, String realName, Long deptId, String deptName,
            String title, String phone, String email, UserStatus status, boolean builtin, boolean locked,
            List<RoleBrief> roles, LocalDateTime lastLoginAt, String lastLoginIp, LocalDateTime createdAt,
            String remark) {
    }

    public record UserOption(Long id, String realName, String employeeNo, String deptName) {
    }

    public record ResetPasswordResult(String password) {
    }
}
