package com.hongyu.water.system.log;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.system.log.LogService.LogQuery;

@RestController
@RequestMapping("/api/system/logs")
@PreAuthorize("hasAuthority('system:log')")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/oper")
    public ApiResponse<PageResult<SysOperLog>> oper(LogQuery q) {
        return ApiResponse.ok(logService.pageOper(q));
    }

    @GetMapping("/login")
    public ApiResponse<PageResult<SysLoginLog>> login(LogQuery q) {
        return ApiResponse.ok(logService.pageLogin(q));
    }
}
