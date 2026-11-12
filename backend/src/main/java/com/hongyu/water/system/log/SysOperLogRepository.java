package com.hongyu.water.system.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysOperLogRepository extends JpaRepository<SysOperLog, Long>, JpaSpecificationExecutor<SysOperLog> {
}
