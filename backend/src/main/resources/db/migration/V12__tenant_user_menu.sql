-- P11: user tenant assignment + tenant management menu

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES sys_tenant(id);

UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user(tenant_id);

INSERT INTO sys_permission (id, code, name, type) VALUES
(31, 'system:tenant:read', '查看租户', 'api'),
(32, 'system:tenant:write', '管理租户', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 31), (1, 32);

INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(17, '租户管理', '/system/tenant', 'ApartmentOutlined', './system/tenant', 2, 0, 31, FALSE);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
