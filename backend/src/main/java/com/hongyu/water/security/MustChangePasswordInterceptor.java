package com.hongyu.water.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hongyu.water.common.BizException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 使用初始密码 / 被重置密码的用户，必须先修改密码才能访问业务接口（需求 F-103）。
 */
@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser u = SecurityUtils.currentOrNull();
        if (u != null && u.mustChangePwd()) {
            throw new BizException(HttpStatus.FORBIDDEN, "请先修改初始密码");
        }
        return true;
    }
}
