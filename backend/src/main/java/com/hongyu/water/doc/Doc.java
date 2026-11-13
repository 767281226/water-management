package com.hongyu.water.doc;

import java.time.LocalDateTime;

import com.hongyu.water.common.BaseEntity;
import com.hongyu.water.doc.storage.StorageType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "biz_doc")
public class Doc extends BaseEntity {

    /** 密级 */
    public enum Level {
        PUBLIC("公开"), INTERNAL("内部"), SECRET("涉密");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status { ON, OFF }

    @Column(name = "category_id")
    private Long categoryId;

    private String name;
    private String code;
    private String version;
    private Integer year;

    @Enumerated(EnumType.STRING)
    private Level level = Level.INTERNAL;

    @Column(name = "need_approval")
    private boolean needApproval = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type")
    private StorageType storageType;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_ext")
    private String fileExt;

    @Column(name = "file_size")
    private long fileSize;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @Column(name = "source_missing")
    private boolean sourceMissing;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ON;

    @Column(name = "view_count")
    private int viewCount;

    @Column(name = "download_count")
    private int downloadCount;

    private String remark;

    @Column(name = "uploader_id")
    private Long uploaderId;

    private boolean deleted;

    public boolean available() {
        return !deleted && status == Status.ON;
    }
}
