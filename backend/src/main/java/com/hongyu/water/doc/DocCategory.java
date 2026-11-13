package com.hongyu.water.doc;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "biz_doc_category")
public class DocCategory extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    private String name;
    private int sort;
}
