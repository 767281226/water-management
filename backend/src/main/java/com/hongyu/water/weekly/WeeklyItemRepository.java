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

    @Query("select i from WeeklyItem i join fetch i.plan p where i.projectId = :projectId "
            + "order by p.weekStart desc, p.userId asc, i.sort asc")
    List<WeeklyItem> findRecentByProject(Long projectId, Pageable pageable);
}
