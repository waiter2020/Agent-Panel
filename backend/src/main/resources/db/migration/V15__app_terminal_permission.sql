-- Web terminal permission for in-container console access

INSERT INTO sys_permission (id, code, name, type) VALUES
(42, 'app:terminal', '应用终端', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 42),
(2, 42),
(3, 42);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
