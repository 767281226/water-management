package com.hongyu.water.score;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    @EntityGraph(attributePaths = "items")
    List<Score> findByMonthOrderByIdAsc(String month);

    @EntityGraph(attributePaths = "items")
    List<Score> findByMonthStartingWithAndStatus(String yearPrefix, Score.Status status);

    @EntityGraph(attributePaths = "items")
    Optional<Score> findWithItemsById(Long id);

    Optional<Score> findByProjectId(Long projectId);

    boolean existsByProjectId(Long projectId);

    List<Score> findByStatusAndProjectIdIn(Score.Status status, Collection<Long> projectIds);
}
