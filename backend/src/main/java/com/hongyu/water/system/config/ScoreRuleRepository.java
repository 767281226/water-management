package com.hongyu.water.system.config;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRuleRepository extends JpaRepository<ScoreRule, Long> {

    List<ScoreRule> findAllByOrderBySortAscIdAsc();
}
