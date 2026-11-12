package com.hongyu.water.system.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysConfigRepository extends JpaRepository<SysConfig, String> {

    List<SysConfig> findByGroupCodeOrderBySortAsc(String groupCode);
}
