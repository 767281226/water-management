package com.hongyu.water.doc;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.common.ApiResponse;
import com.hongyu.water.doc.DocDtos.CategoryForm;
import com.hongyu.water.doc.DocDtos.CategoryNode;
import com.hongyu.water.system.log.OperLog;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/doc-categories")
public class DocCategoryController {

    private final DocCategoryService service;

    public DocCategoryController(DocCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('doc')")
    public ApiResponse<List<CategoryNode>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @OperLog(module = "资料维护", action = "新增分类")
    @PostMapping
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody CategoryForm f) {
        return ApiResponse.ok(service.create(f));
    }

    @OperLog(module = "资料维护", action = "修改分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryForm f) {
        service.update(id, f);
        return ApiResponse.ok();
    }

    @OperLog(module = "资料维护", action = "删除分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('doc:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
