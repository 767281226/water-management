package com.hongyu.water.system.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.hongyu.water.system.role.DataScope;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 认证相关请求 / 响应。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入账号") @Size(max = 64) String username,
            @NotBlank(message = "请输入密码") @Size(max = 64) String password) {
    }

    public record LoginResponse(String token, long expiresIn, boolean mustChangePwd) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码") String newPassword) {
    }

    public record ProfileRequest(
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") String phone,
            @Email(message = "邮箱格式不正确") @Size(max = 128) String email) {
    }

    public record MeResponse(Long id, String username, String realName, String employeeNo, Long deptId,
            String deptName, String title, String phone, String email, boolean superAdmin, DataScope dataScope,
            List<String> roles, Set<String> permissions, boolean mustChangePwd, LocalDateTime lastLoginAt) {
    }
}
