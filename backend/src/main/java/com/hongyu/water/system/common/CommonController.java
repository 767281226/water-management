package com.hongyu.water.system.common;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.system.dept.DeptDtos.DeptNode;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.user.UserDtos.UserOption;
import com.hongyu.water.system.user.UserService;

/**
 * 业务页面通用的下拉数据（登录即可访问）：人员、部门。
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    private final UserService userService;
    private final DeptService deptService;

    public CommonController(UserService userService, DeptService deptService) {
        this.userService = userService;
        this.deptService = deptService;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserOption>> users() {
        return ApiResponse.ok(userService.options());
    }

    @GetMapping("/depts")
    public ApiResponse<List<DeptNode>> depts() {
        return ApiResponse.ok(deptService.tree());
    }
}
