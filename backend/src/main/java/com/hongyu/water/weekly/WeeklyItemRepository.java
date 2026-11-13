package com.hongyu.water.weekly;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WeeklyItemRepository extends JpaRepository<WeeklyItem, Long> {

    long countByProjectId(Long projectId);

    /** 项目累计工时：已填完成情况的按实际工时，否则按计划工时 */
    @Query("select coalesce(sum(coalesce(i.actualDays, i.planDays)), 0) from WeeklyItem i where i.projectId = :projectId")
    BigDecimal sumDaysByProject(Long projectId);

    /** 项目各成员工时：[userId, days] */
    @Query("select p.userId, coalesce(sum(coalesce(i.actualDays, i.planDays)), 0) from WeeklyItem i join i.plan p "
            + "where i.projectId = :projectId group by p.userId")
    List<Object[]> sumDaysByUserForProject(Long projectId);

    /** 项目计划事项的平均完成比例 */
    @Query("select avg(i.completion) from WeeklyItem i where i.projectId = :projectId and i.extra = false and i.completion is not null")
    Double avgCompletionByProject(Long projectId);

    /** 涉及该项目且逾期提交的周报数 */
    @Query("select count(distinct p.id) from WeeklyItem i join i.plan p where i.projectId = :projectId "
            + "and (p.planOverdue = true or p.reportOverdue = true)")
    long countOverduePlansByProject(Long projectId);

    /** 某时间段内每人的工时：[userId, days] */
    @Query("select p.userId, coalesce(sum(coalesce(i.actualDays, i.planDays)), 0) from WeeklyItem i join i.plan p "
            + "where p.weekStart between :from and :to group by p.userId")
    List<Object[]> sumDaysByUserBetween(java.time.LocalDate from, java.time.LocalDate to);

    @Query("select i from WeeklyItem i join fetch i.plan p where i.projectId = :projectId "
            + "order by p.weekStart desc, p.userId asc, i.sort asc")
    List<WeeklyItem> findRecentByProject(Long projectId, Pageable pageable);
}
