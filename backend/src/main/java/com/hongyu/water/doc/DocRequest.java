package com.hongyu.water.doc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 资料下载申请。
 */
@Getter
@Setter
@Entity
@Table(name = "biz_doc_request")
public class DocRequest extends BaseEntity {

    public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }

    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "project_id")
    private Long projectId;

    private String purpose;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "max_downloads")
    private int maxDownloads;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "biz_doc_request_item", joinColumns = @JoinColumn(name = "request_id"))
    private List<Item> items = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNo asc")
    private List<DocRequestStep> steps = new ArrayList<>();

    /** 当前待审批的步骤 */
    public Optional<DocRequestStep> currentStep() {
        return steps.stream().filter(s -> s.getStatus() == DocRequestStep.Status.PENDING).findFirst();
    }

    /** 审批通过且在有效期内 */
    public boolean valid(LocalDateTime now) {
        return status == Status.APPROVED && expireAt != null && expireAt.isAfter(now);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Embeddable
    public static class Item {

        @Column(name = "doc_id")
        private Long docId;

        @Column(name = "download_count")
        private int downloadCount;

        public Item(Long docId) {
            this.docId = docId;
        }
    }
}
