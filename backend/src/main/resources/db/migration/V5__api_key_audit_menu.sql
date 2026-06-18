-- API keys for /v1 inference auth
CREATE TABLE api_key (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    key_prefix      VARCHAR(16) NOT NULL,
    key_hash        VARCHAR(128) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    scopes          JSONB NOT NULL DEFAULT '["ai:chat"]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Dev inference key: apk_agentpanel_dev_inference_key (change in production)
INSERT INTO api_key (name, key_prefix, key_hash, scopes) VALUES
('默认推理密钥', 'apk_agent', '2c92f705c02660c953e5472d0ecc26009feccd27c7cf3abcba0dd4d8428523fd', '["ai:chat"]');

-- System settings permissions
INSERT INTO sys_permission (id, code, name, type) VALUES
(18, 'system:setting:read', '查看系统设置', 'api'),
(19, 'system:setting:write', '管理系统设置', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 18), (1, 19), (2, 18), (2, 19);

-- Audit log menu
INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(12, '审计日志', '/system/audit', 'AuditOutlined', './system/audit', 2, 4, 17, FALSE);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
