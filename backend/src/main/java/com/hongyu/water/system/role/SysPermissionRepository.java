package com.hongyu.water.system.role;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPermissionRepository extends JpaRepository<SysPermission, String> {

    List<SysPermission> findAllByOrderBySortAsc();
}
