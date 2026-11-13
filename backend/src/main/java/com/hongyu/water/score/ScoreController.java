package com.hongyu.water.score;

import java.util.List;

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
import com.hongyu.water.score.ScoreDtos.GradeRequest;
import com.hongyu.water.score.ScoreDtos.MonthResponse;
import com.hongyu.water.score.ScoreDtos.RuleRef;
import com.hongyu.water.score.ScoreDtos.ScoreVO;
import com.hongyu.water.score.ScoreDtos.TodoResponse;
import com.hongyu.water.score.ScoreDtos.UnpublishRequest;
import com.hongyu.water.security.SecurityUtils;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    private final ScoreService service;

    public ScoreController(ScoreService service) {
        this.service = service;
    }

    /** 某月考评清单，month 为空时默认上月 */
    @GetMapping
    @PreAuthorize("hasAuthority('score')")
    public ApiResponse<MonthResponse> month(@RequestParam(required = false) String month) {
        return ApiResponse.ok(service.month(month, SecurityUtils.current()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('score')")
    public ApiResponse<ScoreVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id, SecurityUtils.current()));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('score')")
    public ApiResponse<List<RuleRef>> rules() {
        return ApiResponse.ok(service.enabledRules());
    }

    @GetMapping("/todo")
    public ApiResponse<TodoResponse> todo() {
        return ApiResponse.ok(service.todo(SecurityUtils.current()));
    }

    @OperLog(module = "月度考评", action = "考评打分")
    @PutMapping("/{id}/grade")
    @PreAuthorize("hasAuthority('score:grade')")
    public ApiResponse<ScoreVO> grade(@PathVariable Long id, @Valid @RequestBody GradeRequest req) {
        return ApiResponse.ok(service.grade(id, req, SecurityUtils.current()));
    }

    @OperLog(module = "月度考评", action = "发布考评")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('score:publish')")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        service.publish(id, SecurityUtils.current());
        return ApiResponse.ok();
    }

    @OperLog(module = "月度考评", action = "批量发布考评")
    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('score:publish')")
    public ApiResponse<Integer> publishMonth(@RequestParam String month) {
        return ApiResponse.ok(service.publishMonth(month, SecurityUtils.current()));
    }

    /** 撤回发布（原因记录在操作日志中） */
    @OperLog(module = "月度考评", action = "撤回发布")
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('score:publish')")
    public ApiResponse<Void> unpublish(@PathVariable Long id, @Valid @RequestBody UnpublishRequest req) {
        service.unpublish(id, SecurityUtils.current());
        return ApiResponse.ok();
    }
}
