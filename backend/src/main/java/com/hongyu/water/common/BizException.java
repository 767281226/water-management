package com.hongyu.water.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * 业务异常，消息会直接展示给用户。
 */
@Getter
public class BizException extends RuntimeException {

    private final HttpStatus status;

    public BizException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static BizException notFound(String what) {
        return new BizException(HttpStatus.NOT_FOUND, what + "不存在");
    }
}
