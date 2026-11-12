package com.hongyu.water.system.dept;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.system.dept.DeptDtos.DeptForm;
import com.hongyu.water.system.dept.DeptDtos.DeptNode;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;

@Service
public class DeptService {

    private final SysDeptRepository deptRepo;
    private final SysUserRepository userRepo;

    public DeptService(SysDeptRepository deptRepo, SysUserRepository userRepo) {
        this.deptRepo = deptRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public List<DeptNode> tree() {
        List<SysDept> all = deptRepo.findAllByOrderBySortAscIdAsc();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : userRepo.countGroupByDept()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Set<Long> leaderIds = all.stream().map(SysDept::getLeaderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leaderNames = userRepo.findByIdIn(leaderIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));

        Map<Long, DeptNode> nodes = new LinkedHashMap<>();
        for (SysDept d : all) {
            nodes.put(d.getId(), new DeptNode(d.getId(), d.getParentId(), d.getName(), d.getLeaderId(),
                    leaderNames.get(d.getLeaderId()), d.getSort(), counts.getOrDefault(d.getId(), 0L), null));
        }
        List<DeptNode> roots = new ArrayList<>();
        for (DeptNode n : nodes.values()) {
            DeptNode parent = n.parentId() == null ? null : nodes.get(n.parentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.children().add(n);
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> nameMap() {
        return deptRepo.findAll().stream().collect(Collectors.toMap(SysDept::getId, SysDept::getName));
    }

    @Transactional(readOnly = true)
    public String nameOf(Long id) {
        return id == null ? null : deptRepo.findById(id).map(SysDept::getName).orElse(null);
    }

    /** 部门及其全部下级部门 ID。 */
    @Transactional(readOnly = true)
    public Set<Long> selfAndDescendants(Long id) {
        List<SysDept> all = deptRepo.findAll();
        Map<Long, List<Long>> children = new HashMap<>();
        for (SysDept d : all) {
            if (d.getParentId() != null) {
                children.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>(List.of(id));
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (result.add(cur)) {
                stack.addAll(children.getOrDefault(cur, List.of()));
            }
        }
        return result;
    }

    @Transactional
    public Long create(DeptForm form) {
        SysDept d = new SysDept();
        apply(d, form);
        return deptRepo.save(d).getId();
    }

    @Transactional
    public void update(Long id, DeptForm form) {
        SysDept d = mustGet(id);
        if (form.parentId() != null && selfAndDescendants(id).contains(form.parentId())) {
            throw new BizException("上级部门不能是本部门或其下级部门");
        }
        apply(d, form);
    }

    @Transactional
    public void delete(Long id) {
        SysDept d = mustGet(id);
        if (deptRepo.existsByParentId(id)) {
            throw new BizException("请先删除下级部门");
        }
        if (userRepo.countByDeptIdInAndDeletedFalse(List.of(id)) > 0) {
            throw new BizException("部门下还有人员，请先调整人员所属部门");
        }
        userRepo.detachDeletedFromDept(id);
        deptRepo.delete(d);
    }

    private void apply(SysDept d, DeptForm form) {
        if (form.parentId() != null) {
            mustGet(form.parentId());
        }
        if (form.leaderId() != null && userRepo.findWithRolesByIdAndDeletedFalse(form.leaderId()).isEmpty()) {
            throw new BizException("部门负责人不存在");
        }
        String name = form.name().trim();
        boolean dup = deptRepo.findAll().stream().anyMatch(o -> !Objects.equals(o.getId(), d.getId())
                && Objects.equals(o.getParentId(), form.parentId()) && o.getName().equals(name));
        if (dup) {
            throw new BizException("同级部门中已存在「" + name + "」");
        }
        d.setParentId(form.parentId());
        d.setName(name);
        d.setLeaderId(form.leaderId());
        d.setSort(form.sort() == null ? 0 : form.sort());
    }

    @Transactional(readOnly = true)
    public SysDept mustGet(Long id) {
        return deptRepo.findById(id).orElseThrow(() -> BizException.notFound("部门"));
    }

    /** 用户被删除时，清除其部门负责人身份。 */
    @Transactional
    public void clearLeader(Long userId) {
        deptRepo.findAll().stream().filter(d -> Objects.equals(d.getLeaderId(), userId)).forEach(d -> d.setLeaderId(null));
    }
}
