package com.hongyu.water.common;

/**
 * 密码规则：至少 8 位，必须同时包含字母和数字（需求 F-103）。
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void check(String pwd) {
        if (pwd == null || pwd.length() < 8 || pwd.length() > 32) {
            throw new BizException("密码长度应为 8~32 位");
        }
        if (!pwd.matches(".*[A-Za-z].*") || !pwd.matches(".*\\d.*")) {
            throw new BizException("密码必须同时包含字母和数字");
        }
        if (pwd.contains(" ")) {
            throw new BizException("密码不能包含空格");
        }
    }
}
