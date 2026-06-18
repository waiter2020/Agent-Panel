-- API key expires_at + management permissions/menus, system settings menu

ALTER TABLE api_key ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

INSERT INTO sys_permission (id, code, name, type) VALUES
(20, 'system:apikey:read', '查看 API 密钥', 'api'),
(21, 'system:apikey:write', '管理 API 密钥', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 20), (1, 21);

INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(13, 'API 密钥', '/system/apikey', 'KeyOutlined', './system/apikey', 2, 5, 20, FALSE),
(14, '系统设置', '/system/settings', 'ControlOutlined', './system/settings', 2, 6, 18, FALSE);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
