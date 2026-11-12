-- =====================================================================
-- 项目台账、周计划 / 周完成情况
-- =====================================================================

-- 项目
create table biz_project (
    id               bigserial primary key,
    code             varchar(32)  not null,
    name             varchar(128) not null,
    type             varchar(32)  not null,
    client           varchar(128),
    leader_id        bigint       not null references sys_user (id),
    dept_id          bigint       references sys_dept (id),
    contract_amount  numeric(14, 2),
    start_date       date,
    plan_end_date    date         not null,
    actual_end_date  date,
    status           varchar(16)  not null default 'NOT_STARTED',
    archive_applied  boolean      not null default false,
    archive_reject_reason varchar(255),
    archived_at      timestamp(0),
    archived_by      bigint       references sys_user (id),
    remark           varchar(500),
    deleted          boolean      not null default false,
    created_by       bigint       references sys_user (id),
    created_at       timestamp(0) not null default now(),
    updated_at       timestamp(0) not null default now(),
    constraint ck_project_status check (status in ('NOT_STARTED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'TERMINATED', 'ARCHIVED')),
    constraint ck_project_dates check (start_date is null or plan_end_date >= start_date)
);
create unique index uk_project_code on biz_project (code) where not deleted;
create index idx_project_dept on biz_project (dept_id);
create index idx_project_leader on biz_project (leader_id);
comment on table biz_project is '项目台账';
comment on column biz_project.contract_amount is '合同金额（万元）';
comment on column biz_project.archive_applied is '项目负责人已申请存档，等待部门负责人确认';

create table biz_project_member (
    project_id bigint not null references biz_project (id) on delete cascade,
    user_id    bigint not null references sys_user (id),
    primary key (project_id, user_id)
);
create index idx_project_member_user on biz_project_member (user_id);
comment on table biz_project_member is '项目参与人员（不含项目负责人）';

-- 周报（每人每周一条）
create table biz_weekly_plan (
    id                  bigserial primary key,
    user_id             bigint       not null references sys_user (id),
    week_start          date         not null,
    plan_status         varchar(16)  not null default 'SUBMITTED',
    plan_submitted_at   timestamp(0) not null,
    plan_overdue        boolean      not null default false,
    report_status       varchar(16)  not null default 'NOT_SUBMITTED',
    report_submitted_at timestamp(0),
    report_overdue      boolean      not null default false,
    completion_rate     int,
    comment             varchar(500),
    comment_by          bigint       references sys_user (id),
    comment_at          timestamp(0),
    return_reason       varchar(255),
    created_at          timestamp(0) not null default now(),
    updated_at          timestamp(0) not null default now(),
    constraint uk_weekly_user_week unique (user_id, week_start),
    constraint ck_weekly_monday check (extract(isodow from week_start) = 1),
    constraint ck_weekly_plan_status check (plan_status in ('SUBMITTED', 'RETURNED')),
    constraint ck_weekly_report_status check (report_status in ('NOT_SUBMITTED', 'SUBMITTED')),
    constraint ck_weekly_rate check (completion_rate is null or completion_rate between 0 and 100)
);
create index idx_weekly_week on biz_weekly_plan (week_start);
comment on table biz_weekly_plan is '周报：周一填计划，周五填完成情况';
comment on column biz_weekly_plan.week_start is '所在周的周一';
comment on column biz_weekly_plan.plan_status is 'SUBMITTED 已提交 / RETURNED 被退回重填';
comment on column biz_weekly_plan.completion_rate is '本周计划完成率（%），计划事项完成比例的平均值';

create table biz_weekly_item (
    id             bigserial primary key,
    plan_id        bigint       not null references biz_weekly_plan (id) on delete cascade,
    project_id     bigint       references biz_project (id),
    content        varchar(255) not null,
    target         varchar(255),
    plan_days      numeric(4, 1),
    remark         varchar(255),
    extra          boolean      not null default false,
    completion     int,
    actual_content varchar(255),
    actual_days    numeric(4, 1),
    reason         varchar(255),
    sort           int          not null default 0,
    constraint ck_weekly_item_completion check (completion is null or completion between 0 and 100),
    constraint ck_weekly_item_days check ((plan_days is null or plan_days between 0 and 7)
        and (actual_days is null or actual_days between 0 and 7))
);
create index idx_weekly_item_plan on biz_weekly_item (plan_id);
create index idx_weekly_item_project on biz_weekly_item (project_id);
comment on table biz_weekly_item is '周报事项';
comment on column biz_weekly_item.project_id is '关联项目；为空表示其他工作（会议、投标、培训等）';
comment on column biz_weekly_item.extra is '计划外工作（周五填报时新增）';

-- 项目类型（参数配置 › 项目设置）
insert into sys_config (config_key, config_value, group_code, name, remark, sort) values
    ('project.types', '规划,设计,咨询,监理,测绘,勘察', 'project', '项目类型', '多个用逗号分隔', 1),
    ('project.code-prefix', 'HY', 'project', '项目编号前缀', '新建项目时自动生成编号：前缀-年份-序号', 2);

-- 截止时间格式说明：周X HH:mm，下周X 表示次周
update sys_config set remark = '格式：周X HH:mm，例如"周一 23:59"；超过截止时间填报标记为逾期' where config_key = 'fill.plan.deadline';
update sys_config set remark = '格式：周X HH:mm 或 下周X HH:mm，例如"周五 23:59"、"下周一 12:00"' where config_key = 'fill.report.deadline';
