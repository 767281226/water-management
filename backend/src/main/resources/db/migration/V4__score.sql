-- =====================================================================
-- 月度考评：项目存档后进入存档当月的考评清单
-- =====================================================================

create table biz_score (
    id           bigserial primary key,
    project_id   bigint       not null unique references biz_project (id),
    month        varchar(7)   not null,
    status       varchar(16)  not null default 'PENDING',
    total        numeric(5, 1),
    comment      varchar(500),
    scorer_id    bigint       references sys_user (id),
    scored_at    timestamp(0),
    publisher_id bigint       references sys_user (id),
    published_at timestamp(0),
    created_at   timestamp(0) not null default now(),
    updated_at   timestamp(0) not null default now(),
    constraint ck_score_status check (status in ('PENDING', 'SCORED', 'PUBLISHED')),
    constraint ck_score_month check (month ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    constraint ck_score_total check (total is null or total between 0 and 100)
);
create index idx_score_month on biz_score (month);
comment on table biz_score is '月度考评：每个存档项目一条';
comment on column biz_score.month is '考评月份（项目存档所在月），YYYY-MM';
comment on column biz_score.status is 'PENDING 待打分 / SCORED 已打分待发布 / PUBLISHED 已发布';
comment on column biz_score.total is '加权总分 = Σ 指标得分 × 权重 / 100';

create table biz_score_item (
    id        bigserial primary key,
    score_id  bigint      not null references biz_score (id) on delete cascade,
    rule_key  varchar(32) not null,
    rule_name varchar(64) not null,
    weight    int         not null,
    score     int         not null,
    sort      int         not null default 0,
    constraint ck_score_item_score check (score between 0 and 100),
    constraint ck_score_item_weight check (weight between 0 and 100)
);
create index idx_score_item_score on biz_score_item (score_id);
comment on table biz_score_item is '考评指标得分；保存打分时的指标名称和权重快照，后续调整指标不影响历史结果';

-- 已存档项目补齐考评记录
insert into biz_score (project_id, month)
select id, to_char(archived_at, 'YYYY-MM') from biz_project
where status = 'ARCHIVED' and not deleted and archived_at is not null;
