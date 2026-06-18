-- Role permission seeds per design doc 6.3
-- SUPER_ADMIN already has all permissions from V2

-- ADMIN: user/app/model/file management (no audit, no role/menu write)
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 1), (2, 2),   -- system:user:read/write
(2, 3),           -- system:role:read
(2, 5),           -- system:menu:read
(2, 7), (2, 8), (2, 9), (2, 10), (2, 11),  -- app:*
(2, 12), (2, 13), -- file:*
(2, 14), (2, 15), (2, 16);  -- ai:*

-- OPERATOR: app deploy & ops (read + deploy + operate)
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 7), (3, 9), (3, 10);

-- VIEWER: read only
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(4, 7), (4, 12), (4, 14);
