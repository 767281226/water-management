package com.hongyu.water.system.role;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleForm(
            @NotBlank(message = "请输入角色编码")
            @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$", message = "角色编码为 2~32 位小写字母、数字或下划线，且以字母开头")
            String code,
            @NotBlank(message = "请输入角色名称") @Size(max = 64, message = "角色名称不超过 64 个字") String name,
            @NotNull(message = "请选择数据范围") DataScope dataScope,
            @Min(0) @Max(9999) Integer sort,
            @Size(max = 255, message = "备注不超过 255 个字") String remark,
            Set<String> permissions) {
    }

    public record RoleVO(Long id, String code, String name, DataScope dataScope, boolean builtin, boolean superAdmin,
            int sort, String remark, Set<String> permissions, long userCount, LocalDateTime createdAt) {
    }

    public record PermissionNode(String code, String name, String type, List<PermissionNode> children) {

        public PermissionNode {
            children = children == null ? new ArrayList<>() : children;
        }
    }
}
