package com.hongyu.water.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    Optional<Project> findByIdAndDeletedFalse(Long id);

    @Query("select count(p) > 0 from Project p where p.deleted = false and p.code = :code and p.id <> :excludeId")
    boolean codeTaken(String code, long excludeId);

    @Query("select p.code from Project p where p.code like :prefix")
    List<String> findCodesLike(String prefix);

    /** 我负责或参与的项目 */
    @Query("select distinct p from Project p left join p.memberIds m where p.deleted = false "
            + "and (p.leaderId = :userId or m = :userId) order by p.id desc")
    List<Project> findInvolving(Long userId);

    List<Project> findByIdIn(Collection<Long> ids);

    List<Project> findByDeletedFalseAndStatusAndArchivedAtBetween(ProjectStatus status, java.time.LocalDateTime from,
            java.time.LocalDateTime to);
}
