package com.hongyu.water.weekly;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<WeeklyPlan> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    @EntityGraph(attributePaths = "items")
    Optional<WeeklyPlan> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<WeeklyPlan> findByWeekStartAndUserIdIn(LocalDate weekStart, Collection<Long> userIds);

    Page<WeeklyPlan> findByUserIdOrderByWeekStartDesc(Long userId, Pageable pageable);

    /** 某时间段内每人提交的周计划数：[userId, count] */
    @org.springframework.data.jpa.repository.Query("select p.userId, count(p) from WeeklyPlan p "
            + "where p.planStatus = :status and p.weekStart between :from and :to group by p.userId")
    List<Object[]> countByStatusAndUserBetween(WeeklyPlan.PlanStatus status, LocalDate from, LocalDate to);
}
