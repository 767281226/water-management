package com.hongyu.water.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hongyu.water.common.BizException;

/**
 * 获取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前用户；未登录时返回 null。 */
    public static CurrentUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser u ? u : null;
    }

    public static CurrentUser current() {
        CurrentUser u = currentOrNull();
        if (u == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return u;
    }
}
