package com.hongyu.water.doc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.doc.DocDtos.CategoryForm;
import com.hongyu.water.doc.DocDtos.CategoryNode;

/**
 * 资料分类（两级）。
 */
@Service
public class DocCategoryService {

    private final DocCategoryRepository repo;
    private final DocRepository docRepo;

    public DocCategoryService(DocCategoryRepository repo, DocRepository docRepo) {
        this.repo = repo;
        this.docRepo = docRepo;
    }

    /** 分类树，docCount 为上架资料数（一级分类含下级） */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<DocCategory> all = repo.findAllByOrderBySortAscIdAsc();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : docRepo.countByCategory(Doc.Status.ON)) {
            counts.put((Long) r[0], (Long) r[1]);
        }
        Map<Long, CategoryNode> nodes = new LinkedHashMap<>();
        for (DocCategory c : all) {
            nodes.put(c.getId(), new CategoryNode(c.getId(), c.getParentId(), c.getName(), c.getSort(), counts.getOrDefault(c.getId(), 0L), null));
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode n : nodes.values()) {
            CategoryNode parent = n.parentId() == null ? null : nodes.get(n.parentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.children().add(n);
            }
        }
        // 一级分类合计下级数量
        return roots.stream().map(r -> new CategoryNode(r.id(), r.parentId(), r.name(), r.sort(),
                r.docCount() + r.children().stream().mapToLong(CategoryNode::docCount).sum(), r.children())).toList();
    }

    /** 分类路径：一级 / 二级 */
    @Transactional(readOnly = true)
    public Map<Long, String> paths() {
        List<DocCategory> all = repo.findAll();
        Map<Long, DocCategory> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        Map<Long, String> result = new HashMap<>();
        for (DocCategory c : all) {
            DocCategory p = c.getParentId() == null ? null : byId.get(c.getParentId());
            result.put(c.getId(), p == null ? c.getName() : p.getName() + " / " + c.getName());
        }
        return result;
    }

    /** 分类及其下级 */
    @Transactional(readOnly = true)
    public Set<Long> selfAndChildren(Long id) {
        Set<Long> ids = new HashSet<>();
        ids.add(id);
        repo.findAll().stream().filter(c -> id.equals(c.getParentId())).forEach(c -> ids.add(c.getId()));
        return ids;
    }

    @Transactional
    public Long create(CategoryForm f) {
        DocCategory c = new DocCategory();
        apply(c, f);
        return repo.save(c).getId();
    }

    @Transactional
    public void update(Long id, CategoryForm f) {
        DocCategory c = mustGet(id);
        if (f.parentId() != null && repo.existsByParentId(id)) {
            throw new BizException("该分类下还有子分类，不能设为二级分类");
        }
        apply(c, f);
    }

    @Transactional
    public void delete(Long id) {
        DocCategory c = mustGet(id);
        if (repo.existsByParentId(id)) {
            throw new BizException("请先删除下级分类");
        }
        if (docRepo.existsByCategoryIdAndDeletedFalse(id)) {
            throw new BizException("分类下还有资料，请先移动或删除资料");
        }
        repo.delete(c);
    }

    /** 网盘同步：按目录名查找或创建分类 */
    @Transactional
    public DocCategory findOrCreate(Long parentId, String name) {
        String n = name.length() > 64 ? name.substring(0, 64) : name;
        return (parentId == null ? repo.findFirstByParentIdIsNullAndName(n) : repo.findFirstByParentIdAndName(parentId, n))
                .orElseGet(() -> {
                    DocCategory c = new DocCategory();
                    c.setParentId(parentId);
                    c.setName(n);
                    c.setSort(99);
                    return repo.save(c);
                });
    }

    private void apply(DocCategory c, CategoryForm f) {
        if (f.parentId() != null) {
            DocCategory p = mustGet(f.parentId());
            if (p.getParentId() != null) {
                throw new BizException("资料分类最多两级");
            }
            if (p.getId().equals(c.getId())) {
                throw new BizException("上级分类不能是自己");
            }
        }
        String name = f.name().trim();
        var dup = f.parentId() == null ? repo.findFirstByParentIdIsNullAndName(name) : repo.findFirstByParentIdAndName(f.parentId(), name);
        if (dup.isPresent() && !dup.get().getId().equals(c.getId())) {
            throw new BizException("同级分类中已存在「" + name + "」");
        }
        c.setParentId(f.parentId());
        c.setName(name);
        c.setSort(f.sort() == null ? 0 : f.sort());
    }

    @Transactional(readOnly = true)
    public DocCategory mustGet(Long id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("分类"));
    }
}
