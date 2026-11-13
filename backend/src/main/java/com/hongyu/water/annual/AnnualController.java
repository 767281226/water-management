package com.hongyu.water.annual;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.annual.AnnualDtos.AnnualResponse;
import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.security.SecurityUtils;

@RestController
@RequestMapping("/api/annual")
public class AnnualController {

    private final AnnualService service;

    public AnnualController(AnnualService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('annual')")
    public ApiResponse<AnnualResponse> summary(@RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(service.summary(year, deptId, SecurityUtils.current()));
    }
}
