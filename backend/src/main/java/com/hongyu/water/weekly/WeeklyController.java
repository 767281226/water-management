package com.hongyu.water.weekly;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.log.OperLog;
import com.hongyu.water.weekly.WeeklyDtos.CommentRequest;
import com.hongyu.water.weekly.WeeklyDtos.HistoryRow;
import com.hongyu.water.weekly.WeeklyDtos.MineResponse;
import com.hongyu.water.weekly.WeeklyDtos.PlanVO;
import com.hongyu.water.weekly.WeeklyDtos.ReturnRequest;
import com.hongyu.water.weekly.WeeklyDtos.SavePlanRequest;
import com.hongyu.water.weekly.WeeklyDtos.StatResponse;
import com.hongyu.water.weekly.WeeklyDtos.SubmitReportRequest;
import com.hongyu.water.weekly.WeeklyDtos.TodoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/weekly")
public class WeeklyController {

    private final WeeklyService service;

    public WeeklyController(WeeklyService service) {
        this.service = service;
    }

    /** 我的某一周（默认本周） */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('weekly:plan', 'weekly:report')")
    public ApiResponse<MineResponse> mine(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ApiResponse.ok(service.mine(SecurityUtils.current(), weekStart));
    }

    @GetMapping("/mine/history")
    @PreAuthorize("hasAnyAuthority('weekly:plan', 'weekly:stat')")
    public ApiResponse<PageResult<HistoryRow>> history(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.history(SecurityUtils.current(), page, size));
    }

    @GetMapping("/todo")
    public ApiResponse<TodoResponse> todo() {
        return ApiResponse.ok(service.todo(SecurityUtils.current()));
    }

    @OperLog(module = "周报", action = "提交周计划")
    @PutMapping("/plan")
    @PreAuthorize("hasAuthority('weekly:plan')")
    public ApiResponse<PlanVO> savePlan(@Valid @RequestBody SavePlanRequest req) {
        return ApiResponse.ok(service.savePlan(SecurityUtils.current(), req));
    }

    @OperLog(module = "周报", action = "提交周完成情况")
    @PutMapping("/report")
    @PreAuthorize("hasAuthority('weekly:report')")
    public ApiResponse<PlanVO> submitReport(@Valid @RequestBody SubmitReportRequest req) {
        return ApiResponse.ok(service.submitReport(SecurityUtils.current(), req));
    }

    @GetMapping("/stat")
    @PreAuthorize("hasAuthority('weekly:stat')")
    public ApiResponse<StatResponse> stat(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(service.stat(SecurityUtils.current(), weekStart, deptId));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<PlanVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(SecurityUtils.current(), id));
    }

    @OperLog(module = "周报", action = "点评周报")
    @PutMapping("/plans/{id}/comment")
    @PreAuthorize("hasAuthority('weekly:stat:comment')")
    public ApiResponse<Void> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        service.comment(SecurityUtils.current(), id, req.comment());
        return ApiResponse.ok();
    }

    @OperLog(module = "周报", action = "退回周报")
    @PostMapping("/plans/{id}/return")
    @PreAuthorize("hasAuthority('weekly:stat:comment')")
    public ApiResponse<Void> returnPlan(@PathVariable Long id, @Valid @RequestBody ReturnRequest req) {
        service.returnPlan(SecurityUtils.current(), id, req.reason());
        return ApiResponse.ok();
    }
}
