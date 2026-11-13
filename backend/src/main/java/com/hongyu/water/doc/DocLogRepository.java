package com.hongyu.water.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocLogRepository extends JpaRepository<DocLog, Long>, JpaSpecificationExecutor<DocLog> {
}
