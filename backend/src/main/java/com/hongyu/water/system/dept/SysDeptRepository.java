package com.hongyu.water.system.dept;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findAllByOrderBySortAscIdAsc();

    boolean existsByParentId(Long parentId);
}
