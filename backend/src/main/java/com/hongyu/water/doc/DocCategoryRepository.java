package com.hongyu.water.doc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocCategoryRepository extends JpaRepository<DocCategory, Long> {

    List<DocCategory> findAllByOrderBySortAscIdAsc();

    Optional<DocCategory> findFirstByParentIdIsNullAndName(String name);

    Optional<DocCategory> findFirstByParentIdAndName(Long parentId, String name);

    boolean existsByParentId(Long parentId);
}
