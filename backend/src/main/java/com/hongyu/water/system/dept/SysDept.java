package com.hongyu.water.system.dept;

import com.hongyu.water.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_dept")
public class SysDept extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    private String name;

    /** 部门负责人 */
    @Column(name = "leader_id")
    private Long leaderId;

    private int sort;
}
