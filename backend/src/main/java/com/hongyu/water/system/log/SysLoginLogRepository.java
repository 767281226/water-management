package com.hongyu.water.system.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysLoginLogRepository extends JpaRepository<SysLoginLog, Long>, JpaSpecificationExecutor<SysLoginLog> {
}
