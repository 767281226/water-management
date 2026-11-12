package com.hongyu.water.security;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.hongyu.water.system.dept.DeptService;

/**
 * 数据范围计算：ALL 全公司 / DEPT 本部门及下级 / SELF 仅本人。
 */
@Component
public class DataScopeHelper {

    private final DeptService deptService;

    public DataScopeHelper(DeptService deptService) {
        this.deptService = deptService;
    }

    /**
     * 当前用户可查看的部门。
     *
     * @return null 表示全部部门；SELF 返回空集合
     */
    public Set<Long> visibleDeptIds(CurrentUser cu) {
        return switch (cu.dataScope()) {
            case ALL -> null;
            case DEPT -> cu.deptId() == null ? Set.of() : deptService.selfAndDescendants(cu.deptId());
            case SELF -> Set.of();
        };
    }

    /** 某部门是否在当前用户的数据范围内。 */
    public boolean canSeeDept(CurrentUser cu, Long deptId) {
        Set<Long> ids = visibleDeptIds(cu);
        return ids == null || (deptId != null && ids.contains(deptId));
    }
}
