package com.hongyu.water.common;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求相关工具。
 */
public final class WebUtils {

    private WebUtils() {
    }

    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /** 客户端 IP，兼容 Nginx 反向代理。 */
    public static String clientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        for (String h : new String[] {"X-Forwarded-For", "X-Real-IP"}) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank() && !"unknown".equalsIgnoreCase(v)) {
                return v.split(",")[0].trim();
            }
        }
        String ip = req.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }

    public static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
