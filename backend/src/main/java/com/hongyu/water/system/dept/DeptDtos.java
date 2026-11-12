package com.hongyu.water.system.dept;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DeptDtos {

    private DeptDtos() {
    }

    public record DeptForm(Long parentId,
            @NotBlank(message = "请输入部门名称") @Size(max = 64, message = "部门名称不超过 64 个字") String name,
            Long leaderId,
            @Min(0) @Max(9999) Integer sort) {
    }

    public record DeptNode(Long id, Long parentId, String name, Long leaderId, String leaderName, int sort,
            long userCount, List<DeptNode> children) {

        public DeptNode {
            children = children == null ? new ArrayList<>() : children;
        }
    }
}
