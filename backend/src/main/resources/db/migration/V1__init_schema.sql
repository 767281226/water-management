-- =====================================================================
-- 宏禹水利管理软件 —— 系统管理基础表
-- 由 Flyway 管理：已执行的脚本不得修改，变更请新增 V{n}__xxx.sql
-- =====================================================================

-- 部门
create table sys_dept (
    id          bigserial primary key,
    parent_id   bigint references sys_dept (id),
    name        varchar(64) not null,
    leader_id   bigint,
    sort        int         not null default 0,
    created_at  timestamp(0) not null default now(),
    updated_at  timestamp(0) not null default now()
);
comment on table sys_dept is '部门';
comment on column sys_dept.leader_id is '部门负责人（sys_user.id）';

-- 用户
create table sys_user (
    id               bigserial primary key,
    username         varchar(32)  not null,
    employee_no      varchar(32)  not null,
    real_name        varchar(32)  not null,
    password         varchar(100) not null,
    dept_id          bigint references sys_dept (id),
    title            varchar(64),
    phone            varchar(20),
    email            varchar(128),
    status           varchar(16)  not null default 'ENABLED',
    builtin          boolean      not null default false,
    must_change_pwd  boolean      not null default true,
    token_version    int          not null default 0,
    login_fail_count int          not null default 0,
    locked_until     timestamp(0),
    last_login_at    timestamp(0),
    last_login_ip    varchar(64),
    remark           varchar(255),
    deleted          boolean      not null default false,
    created_at       timestamp(0) not null default now(),
    updated_at       timestamp(0) not null default now(),
    constraint ck_user_status check (status in ('ENABLED', 'DISABLED'))
);
create unique index uk_user_username on sys_user (lower(username)) where not deleted;
create unique index uk_user_employee_no on sys_user (employee_no) where not deleted;
create index idx_user_dept on sys_user (dept_id);
comment on table sys_user is '用户';
comment on column sys_user.username is '登录账号';
comment on column sys_user.employee_no is '工号';
comment on column sys_user.builtin is '内置账号（不可删除、停用）';
comment on column sys_user.must_change_pwd is '下次登录必须修改密码';
comment on column sys_user.token_version is '令牌版本，修改密码 / 停用时递增使旧令牌失效';

alter table sys_dept add constraint fk_dept_leader foreign key (leader_id) references sys_user (id);

-- 角色
create table sys_role (
    id          bigserial primary key,
    code        varchar(32) not null unique,
    name        varchar(64) not null,
    data_scope  varchar(16) not null default 'SELF',
    builtin     boolean     not null default false,
    super_admin boolean     not null default false,
    sort        int         not null default 0,
    remark      varchar(255),
    created_at  timestamp(0) not null default now(),
    updated_at  timestamp(0) not null default now(),
    constraint ck_role_scope check (data_scope in ('ALL', 'DEPT', 'SELF'))
);
comment on table sys_role is '角色';
comment on column sys_role.data_scope is '数据范围：ALL 全公司 / DEPT 本部门 / SELF 本人';
comment on column sys_role.super_admin is '超级管理员角色，自动拥有全部权限';

-- 权限（菜单 + 按钮），由迁移脚本维护
create table sys_permission (
    code        varchar(64) primary key,
    parent_code varchar(64) references sys_permission (code),
    name        varchar(64) not null,
    type        varchar(16) not null,
    sort        int         not null default 0,
    constraint ck_perm_type check (type in ('MENU', 'BUTTON'))
);
comment on table sys_permission is '权限点';

create table sys_role_permission (
    role_id   bigint      not null references sys_role (id) on delete cascade,
    perm_code varchar(64) not null references sys_permission (code) on delete cascade,
    primary key (role_id, perm_code)
);

create table sys_user_role (
    user_id bigint not null references sys_user (id) on delete cascade,
    role_id bigint not null references sys_role (id),
    primary key (user_id, role_id)
);

-- 系统参数（填报规则、安全设置等）
create table sys_config (
    config_key   varchar(64) primary key,
    config_value varchar(500) not null,
    group_code   varchar(32)  not null,
    name         varchar(64)  not null,
    remark       varchar(255),
    sort         int          not null default 0,
    updated_at   timestamp(0) not null default now()
);
comment on table sys_config is '系统参数';

-- 月度考评指标
create table sys_score_rule (
    id          bigserial primary key,
    rule_key    varchar(32) not null unique,
    name        varchar(64) not null,
    weight      int         not null,
    description varchar(255),
    sort        int         not null default 0,
    enabled     boolean     not null default true,
    created_at  timestamp(0) not null default now(),
    updated_at  timestamp(0) not null default now(),
    constraint ck_score_weight check (weight between 0 and 100)
);
comment on table sys_score_rule is '月度考评指标与权重';

-- 操作日志
create table sys_oper_log (
    id         bigserial primary key,
    user_id    bigint,
    username   varchar(32),
    real_name  varchar(32),
    module     varchar(32)  not null,
    action     varchar(64)  not null,
    method     varchar(10),
    uri        varchar(255),
    params     varchar(2000),
    success    boolean      not null,
    error_msg  varchar(500),
    ip         varchar(64),
    cost_ms    bigint,
    created_at timestamp(0) not null default now()
);
create index idx_oper_log_time on sys_oper_log (created_at desc);
comment on table sys_oper_log is '操作日志';

-- 登录日志
create table sys_login_log (
    id         bigserial primary key,
    username   varchar(64),
    success    boolean      not null,
    message    varchar(255),
    ip         varchar(64),
    user_agent varchar(500),
    created_at timestamp(0) not null default now()
);
create index idx_login_log_time on sys_login_log (created_at desc);
comment on table sys_login_log is '登录日志';
