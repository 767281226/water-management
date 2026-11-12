package com.hongyu.water.system.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.WebUtils;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.auth.AuthDtos.ChangePasswordRequest;
import com.hongyu.water.system.auth.AuthDtos.LoginRequest;
import com.hongyu.water.system.auth.AuthDtos.LoginResponse;
import com.hongyu.water.system.auth.AuthDtos.MeResponse;
import com.hongyu.water.system.auth.AuthDtos.ProfileRequest;
import com.hongyu.water.system.log.OperLog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, WebUtils.clientIp(http),
                WebUtils.truncate(http.getHeader("User-Agent"), 500)));
    }

    /** JWT 无状态，退出由前端丢弃令牌；这里仅记录日志。 */
    @OperLog(module = "登录", action = "退出登录")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.current()));
    }

    @OperLog(module = "个人中心", action = "修改密码")
    @PutMapping("/password")
    public ApiResponse<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        return ApiResponse.ok(authService.changePassword(SecurityUtils.current(), req));
    }

    @OperLog(module = "个人中心", action = "修改个人信息")
    @PutMapping("/profile")
    public ApiResponse<Void> profile(@Valid @RequestBody ProfileRequest req) {
        authService.updateProfile(SecurityUtils.current(), req);
        return ApiResponse.ok();
    }
}
