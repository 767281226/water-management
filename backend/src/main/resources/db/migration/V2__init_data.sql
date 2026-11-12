-- =====================================================================
-- 初始化数据：权限点、内置角色、部门、内置管理员、系统参数、考评指标
-- =====================================================================

-- ---------- 权限点（菜单 / 按钮）----------
insert into sys_permission (code, parent_code, name, type, sort) values
    ('dashboard',            null,            '工作台',         'MENU', 10),

    ('project',              null,            '项目管理',       'MENU', 20),
    ('project:ledger',       'project',       '项目台账',       'MENU', 21),
    ('project:ledger:edit',  'project:ledger','新建 / 编辑项目', 'BUTTON', 1),
    ('project:ledger:archive','project:ledger','确认存档',       'BUTTON', 2),
    ('weekly:plan',          'project',       '周计划填报',     'MENU', 22),
    ('weekly:report',        'project',       '周完成情况',     'MENU', 23),
    ('weekly:stat',          'project',       '周报查询统计',   'MENU', 24),
    ('weekly:stat:comment',  'weekly:stat',   '点评 / 退回周报', 'BUTTON', 1),
    ('score',                'project',       '月度考评',       'MENU', 25),
    ('score:grade',          'score',         '考评打分',       'BUTTON', 1),
    ('score:publish',        'score',         '审核发布',       'BUTTON', 2),
    ('annual',               'project',       '年度汇总',       'MENU', 26),

    ('doc',                  null,            '资料中心',       'MENU', 30),
    ('doc:search',           'doc',           '资料查询',       'MENU', 31),
    ('doc:request',          'doc',           '我的下载申请',   'MENU', 32),
    ('doc:approve',          'doc',           '下载审批',       'MENU', 33),
    ('doc:manage',           'doc',           '资料维护',       'MENU', 34),

    ('system',               null,            '系统管理',       'MENU', 90),
    ('system:user',          'system',        '用户管理',       'MENU', 91),
    ('system:user:create',   'system:user',   '新增用户',       'BUTTON', 1),
    ('system:user:update',   'system:user',   '编辑用户',       'BUTTON', 2),
    ('system:user:delete',   'system:user',   '删除用户',       'BUTTON', 3),
    ('system:user:status',   'system:user',   '启用 / 停用',    'BUTTON', 4),
    ('system:user:reset-pwd','system:user',   '重置密码',       'BUTTON', 5),
    ('system:dept',          'system',        '部门管理',       'MENU', 92),
    ('system:dept:edit',     'system:dept',   '新增 / 编辑 / 删除部门', 'BUTTON', 1),
    ('system:role',          'system',        '角色权限',       'MENU', 93),
    ('system:role:edit',     'system:role',   '新增 / 编辑 / 删除角色', 'BUTTON', 1),
    ('system:config',        'system',        '参数配置',       'MENU', 94),
    ('system:config:edit',   'system:config', '修改参数',       'BUTTON', 1),
    ('system:log',           'system',        '日志审计',       'MENU', 95);

-- ---------- 内置角色 ----------
insert into sys_role (code, name, data_scope, builtin, super_admin, sort, remark) values
    ('admin',    '系统管理员', 'ALL',  true, true,  1, '内置超级管理员，自动拥有全部权限'),
    ('leader',   '公司领导',   'ALL',  true, false, 2, '查看全公司数据；涉密资料终审；考评审核发布'),
    ('manager',  '部门负责人', 'DEPT', true, false, 3, '本部门周报统计与点评、存档项目打分、下载审批'),
    ('docadmin', '资料管理员', 'SELF', true, false, 4, '资料上传、更新、下架与网盘同步'),
    ('staff',    '普通员工',   'SELF', true, false, 5, '周报填报、资料查询与下载申请');

-- 普通员工
insert into sys_role_permission (role_id, perm_code)
select r.id, p.code from sys_role r, sys_permission p
where r.code = 'staff' and p.code in (
    'dashboard', 'project', 'project:ledger', 'weekly:plan', 'weekly:report', 'weekly:stat',
    'score', 'annual', 'doc', 'doc:search', 'doc:request');

-- 部门负责人
insert into sys_role_permission (role_id, perm_code)
select r.id, p.code from sys_role r, sys_permission p
where r.code = 'manager' and p.code in (
    'dashboard', 'project', 'project:ledger', 'project:ledger:edit', 'project:ledger:archive',
    'weekly:plan', 'weekly:report', 'weekly:stat', 'weekly:stat:comment',
    'score', 'score:grade', 'annual', 'doc', 'doc:search', 'doc:request', 'doc:approve');

-- 公司领导
insert into sys_role_permission (role_id, perm_code)
select r.id, p.code from sys_role r, sys_permission p
where r.code = 'leader' and p.code in (
    'dashboard', 'project', 'project:ledger', 'project:ledger:edit', 'project:ledger:archive',
    'weekly:plan', 'weekly:report', 'weekly:stat', 'weekly:stat:comment',
    'score', 'score:grade', 'score:publish', 'annual', 'doc', 'doc:search', 'doc:request', 'doc:approve');

-- 资料管理员
insert into sys_role_permission (role_id, perm_code)
select r.id, p.code from sys_role r, sys_permission p
where r.code = 'docadmin' and p.code in ('dashboard', 'doc', 'doc:search', 'doc:request', 'doc:manage');

-- ---------- 部门 ----------
insert into sys_dept (id, parent_id, name, sort) values
    (1, null, '宏禹公司',     0),
    (2, 1,    '规划设计一部', 1),
    (3, 1,    '水工设计部',   2),
    (4, 1,    '综合管理部',   3);
select setval('sys_dept_id_seq', (select max(id) from sys_dept));

-- ---------- 内置管理员 admin / admin123（首次登录强制修改密码）----------
insert into sys_user (username, employee_no, real_name, password, dept_id, title, builtin, must_change_pwd, remark)
values ('admin', 'HY0000', '系统管理员',
        '$2a$10$8LPkLRxGB4QsLDGQwzMHjeF6WlOBQwQv283oGvJF5GG4ith1CAAZa',
        4, '系统管理员', true, true, '内置超级管理员，不可删除、停用');

insert into sys_user_role (user_id, role_id)
select u.id, r.id from sys_user u, sys_role r where u.username = 'admin' and r.code = 'admin';

-- ---------- 系统参数 ----------
insert into sys_config (config_key, config_value, group_code, name, remark, sort) values
    ('fill.plan.remind',      '周一 09:00', 'fill', '周计划提醒时间',     '系统自动提醒未填报人员', 1),
    ('fill.plan.deadline',    '周一 23:59', 'fill', '周计划截止时间',     '超过截止时间填报标记为逾期', 2),
    ('fill.report.remind',    '周五 15:00', 'fill', '完成情况提醒时间',   null, 3),
    ('fill.report.deadline',  '周五 23:59', 'fill', '完成情况截止时间',   null, 4),
    ('fill.overdue.rule',     '每次逾期扣进度分 2 分', 'fill', '逾期处理规则', '计入月度考评', 5),
    ('fill.holiday.rule',     '节假日自动顺延至下一工作日', 'fill', '节假日规则', null, 6),
    ('fill.notify.channels',  'SITE', 'fill', '提醒渠道', '多个用逗号分隔：SITE 站内消息、WECOM 企业微信、SMS 短信', 7),
    ('security.init-password','Hy123456', 'security', '用户初始密码', '新增用户 / 重置密码时使用，用户首次登录须修改', 1),
    ('security.login.max-fail','5', 'security', '登录失败锁定次数', '连续输错密码达到该次数后锁定账号', 2),
    ('security.login.lock-minutes','15', 'security', '锁定时长（分钟）', null, 3);

-- ---------- 月度考评指标 ----------
insert into sys_score_rule (rule_key, name, weight, description, sort) values
    ('progress', '进度完成情况', 30, '是否按计划工期完成；参考周报完成率、逾期次数', 1),
    ('quality',  '成果质量',     40, '校审意见、评审通过情况、返工次数', 2),
    ('workload', '工作量与难度', 20, '项目规模、技术难度、投入工时', 3),
    ('client',   '客户评价',     10, '委托单位满意度反馈', 4);
