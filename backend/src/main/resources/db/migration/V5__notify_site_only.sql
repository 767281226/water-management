-- 提醒渠道只保留站内消息（去除企业微信、短信）
update sys_config
set config_value = 'SITE',
    remark       = '目前仅支持站内消息'
where config_key = 'fill.notify.channels';
