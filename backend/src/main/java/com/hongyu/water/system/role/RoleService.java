package com.hongyu.water.system.role;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.system.role.RoleDtos.PermissionNode;
import com.hongyu.water.system.role.RoleDtos.RoleForm;
import com.hongyu.water.system.role.RoleDtos.RoleVO;

/**
 * 角色与权限。内置角色不可删除、不可改编码；超级管理员角色的权限固定为"全部"。
 */
@Service
public class RoleService {

    private final SysRoleRepository roleRepo;
    private final SysPermissionRepository permRepo;

    public RoleService(SysRoleRepository roleRepo, SysPermissionRepository permRepo) {
        this.roleRepo = roleRepo;
        this.permRepo = permRepo;
    }

    @Transactional(readOnly = true)
    public List<RoleVO> list() {
        Set<String> all = permRepo.findAll().stream().map(SysPermission::getCode).collect(Collectors.toSet());
        return roleRepo.findAllByOrderBySortAscIdAsc().stream().map(r -> new RoleVO(r.getId(), r.getCode(), r.getName(),
                r.getDataScope(), r.isBuiltin(), r.isSuperAdmin(), r.getSort(), r.getRemark(),
                new TreeSet<>(r.isSuperAdmin() ? all : r.getPermissions()), roleRepo.countUsers(r.getId()),
                r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionNode> permissionTree() {
        List<SysPermission> all = permRepo.findAllByOrderBySortAsc();
        Map<String, PermissionNode> nodes = new LinkedHashMap<>();
        for (SysPermission p : all) {
            nodes.put(p.getCode(), new PermissionNode(p.getCode(), p.getName(), p.getType(), null));
        }
        List<PermissionNode> roots = new ArrayList<>();
        for (SysPermission p : all) {
            PermissionNode parent = p.getParentCode() == null ? null : nodes.get(p.getParentCode());
            if (parent == null) {
                roots.add(nodes.get(p.getCode()));
            } else {
                parent.children().add(nodes.get(p.getCode()));
            }
        }
        return roots;
    }

    @Transactional
    public Long create(RoleForm form) {
        if (roleRepo.existsByCode(form.code())) {
            throw new BizException("角色编码「" + form.code() + "」已存在");
        }
        SysRole r = new SysRole();
        r.setCode(form.code());
        apply(r, form);
        r.setPermissions(normalize(form.permissions()));
        return roleRepo.save(r).getId();
    }

    @Transactional
    public void update(Long id, RoleForm form) {
        SysRole r = mustGet(id);
        if (!r.getCode().equals(form.code())) {
            if (r.isBuiltin()) {
                throw new BizException("内置角色的编码不可修改");
            }
            if (roleRepo.existsByCode(form.code())) {
                throw new BizException("角色编码「" + form.code() + "」已存在");
            }
            r.setCode(form.code());
        }
        apply(r, form);
        if (r.isSuperAdmin()) {
            r.setDataScope(DataScope.ALL);
        } else {
            r.setPermissions(normalize(form.permissions()));
        }
    }

    @Transactional
    public void delete(Long id) {
        SysRole r = mustGet(id);
        if (r.isBuiltin()) {
            throw new BizException("内置角色不可删除");
        }
        long users = roleRepo.countUsers(id);
        if (users > 0) {
            throw new BizException("还有 " + users + " 个用户使用该角色，请先调整这些用户的角色");
        }
        roleRepo.delete(r);
    }

    private void apply(SysRole r, RoleForm form) {
        r.setName(form.name().trim());
        r.setDataScope(form.dataScope());
        r.setSort(form.sort() == null ? 0 : form.sort());
        r.setRemark(form.remark() == null || form.remark().isBlank() ? null : form.remark().trim());
    }

    /** 校验权限点存在，并自动补齐上级菜单，保证菜单能正常显示。 */
    private Set<String> normalize(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return new HashSet<>();
        }
        Map<String, SysPermission> all = permRepo.findAll().stream()
                .collect(Collectors.toMap(SysPermission::getCode, p -> p));
        Set<String> result = new HashSet<>();
        for (String c : codes) {
            SysPermission p = all.get(c);
            if (p == null) {
                throw new BizException("权限点不存在：" + c);
            }
            while (p != null && result.add(p.getCode())) {
                p = p.getParentCode() == null ? null : all.get(p.getParentCode());
            }
        }
        return result;
    }

    private SysRole mustGet(Long id) {
        return roleRepo.findById(id).orElseThrow(() -> BizException.notFound("角色"));
    }
}
