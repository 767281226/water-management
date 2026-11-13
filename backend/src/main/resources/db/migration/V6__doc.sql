-- =====================================================================
-- 资料中心：分类、资料、下载申请与审批、访问日志
-- 资料文件可以存放在本地（LOCAL，系统上传）或内部网盘（NAS，同步建立索引，文件仍在网盘）
-- =====================================================================

create table biz_doc_category (
    id         bigserial primary key,
    parent_id  bigint references biz_doc_category (id),
    name       varchar(64)  not null,
    sort       int          not null default 0,
    created_at timestamp(0) not null default now(),
    updated_at timestamp(0) not null default now()
);
create unique index uk_doc_category_name on biz_doc_category (coalesce(parent_id, 0), name);
comment on table biz_doc_category is '资料分类（两级）；网盘同步时一级 / 二级目录对应一级 / 二级分类';

create table biz_doc (
    id                 bigserial primary key,
    category_id        bigint       not null references biz_doc_category (id),
    name               varchar(200) not null,
    code               varchar(64),
    version            varchar(32),
    year               int,
    level              varchar(16)  not null default 'INTERNAL',
    need_approval      boolean      not null default true,
    storage_type       varchar(16)  not null,
    storage_key        varchar(500) not null,
    file_name          varchar(255) not null,
    file_ext           varchar(16),
    file_size          bigint       not null default 0,
    source_modified_at timestamp(0),
    source_missing     boolean      not null default false,
    status             varchar(16)  not null default 'ON',
    view_count         int          not null default 0,
    download_count     int          not null default 0,
    remark             varchar(500),
    uploader_id        bigint       references sys_user (id),
    deleted            boolean      not null default false,
    created_at         timestamp(0) not null default now(),
    updated_at         timestamp(0) not null default now(),
    constraint ck_doc_level check (level in ('PUBLIC', 'INTERNAL', 'SECRET')),
    constraint ck_doc_storage check (storage_type in ('LOCAL', 'NAS')),
    constraint ck_doc_status check (status in ('ON', 'OFF'))
);
create unique index uk_doc_storage on biz_doc (storage_type, storage_key) where not deleted;
create index idx_doc_category on biz_doc (category_id);
comment on table biz_doc is '资料';
comment on column biz_doc.code is '编号 / 标准号，如 SL 252-2017';
comment on column biz_doc.level is '密级：PUBLIC 公开 / INTERNAL 内部 / SECRET 涉密（涉密资料下载需公司领导终审）';
comment on column biz_doc.need_approval is '下载是否需要审批；公开规范可设为免审';
comment on column biz_doc.storage_key is 'LOCAL：本地存储相对路径；NAS：网盘内相对路径';
comment on column biz_doc.source_missing is '网盘同步时源文件已不存在（自动下架）';

create table biz_doc_request (
    id            bigserial primary key,
    applicant_id  bigint       not null references sys_user (id),
    project_id    bigint       references biz_project (id),
    purpose       varchar(500) not null,
    status        varchar(16)  not null default 'PENDING',
    expire_at     timestamp(0),
    max_downloads int          not null default 3,
    finished_at   timestamp(0),
    created_at    timestamp(0) not null default now(),
    updated_at    timestamp(0) not null default now(),
    constraint ck_doc_request_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
create index idx_doc_request_applicant on biz_doc_request (applicant_id);
comment on table biz_doc_request is '资料下载申请';
comment on column biz_doc_request.max_downloads is '每份资料最多下载次数';

create table biz_doc_request_item (
    request_id     bigint not null references biz_doc_request (id) on delete cascade,
    doc_id         bigint not null references biz_doc (id),
    download_count int    not null default 0,
    primary key (request_id, doc_id)
);

create table biz_doc_request_step (
    id            bigserial primary key,
    request_id    bigint      not null references biz_doc_request (id) on delete cascade,
    step_no       int         not null,
    name          varchar(32) not null,
    approver_type varchar(16) not null,
    status        varchar(16) not null,
    approver_id   bigint      references sys_user (id),
    comment       varchar(255),
    acted_at      timestamp(0),
    constraint ck_doc_step_type check (approver_type in ('DEPT', 'LEADER')),
    constraint ck_doc_step_status check (status in ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
create index idx_doc_step_request on biz_doc_request_step (request_id);
comment on table biz_doc_request_step is '审批步骤：DEPT 部门负责人审批 / LEADER 公司领导终审（含涉密资料时）';

create table biz_doc_log (
    id         bigserial primary key,
    doc_id     bigint       references biz_doc (id),
    user_id    bigint       references sys_user (id),
    action     varchar(16)  not null,
    request_id bigint,
    ip         varchar(64),
    detail     varchar(255),
    created_at timestamp(0) not null default now()
);
create index idx_doc_log_time on biz_doc_log (created_at desc);
create index idx_doc_log_doc on biz_doc_log (doc_id);
comment on table biz_doc_log is '资料访问日志：VIEW 预览 / DOWNLOAD 下载 / UPLOAD 上传 / UPDATE 修改 / REPLACE 更新文件 / OFFLINE 下架 / ONLINE 上架 / DELETE 删除 / SYNC 网盘同步';

-- 初始分类（与需求说明书 3.4.1 一致，可在资料维护中调整）
insert into biz_doc_category (name, sort) values ('相关规范', 1), ('公司成果', 2), ('行业资料', 3);
insert into biz_doc_category (parent_id, name, sort)
select c.id, s.name, s.sort from biz_doc_category c
join (values
    ('相关规范', '国家标准', 1), ('相关规范', '水利行业标准', 2), ('相关规范', '地方标准', 3), ('相关规范', '规范性文件', 4),
    ('公司成果', '设计报告', 1), ('公司成果', '规划报告', 2), ('公司成果', '设计图纸', 3), ('公司成果', '可研报告', 4), ('公司成果', '获奖成果', 5),
    ('行业资料', '政策文件', 1), ('行业资料', '技术参考', 2), ('行业资料', '典型案例', 3), ('行业资料', '定额造价', 4)
) as s(parent, name, sort) on s.parent = c.name;

-- 资料参数（参数配置 › 资料设置）
insert into sys_config (config_key, config_value, group_code, name, remark, sort) values
    ('doc.download.valid-days', '7', 'doc', '下载有效期（天）', '审批通过后在有效期内可以下载', 1),
    ('doc.download.max-times', '3', 'doc', '每份资料最多下载次数', '每次申请中每份资料的下载次数上限', 2),
    ('doc.new-days', '30', 'doc', '新资料标识天数', '上传 / 同步后多少天内显示"新"标识', 3),
    ('doc.nas.last-sync', '', 'internal', '网盘上次同步时间', '系统自动维护', 1);
