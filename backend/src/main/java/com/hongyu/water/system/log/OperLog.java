package com.hongyu.water.system.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上，自动记录操作日志（操作人、参数、结果、耗时、IP）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块，如"用户管理" */
    String module();

    /** 操作，如"新增用户" */
    String action();
}
