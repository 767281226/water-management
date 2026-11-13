package com.hongyu.water.doc;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocRequestRepository extends JpaRepository<DocRequest, Long> {

    @EntityGraph(attributePaths = "steps")
    Optional<DocRequest> findWithStepsById(Long id);

    @EntityGraph(attributePaths = "steps")
    Page<DocRequest> findByApplicantIdOrderByIdDesc(Long applicantId, Pageable pageable);

    /** 某人的有效 / 待审批申请（用于资料列表显示"可下载 / 审批中"） */
    List<DocRequest> findByApplicantIdAndStatusIn(Long applicantId, Collection<DocRequest.Status> statuses);

    @EntityGraph(attributePaths = "steps")
    List<DocRequest> findByStatusOrderByIdAsc(DocRequest.Status status);

    /** 我审批过的申请 */
    @EntityGraph(attributePaths = "steps")
    @Query("select distinct r from DocRequest r join r.steps s where s.approverId = :userId order by r.id desc")
    Page<DocRequest> findApprovedBy(Long userId, Pageable pageable);
}
