-- Seed admin user, roles, permissions, menus, templates
-- Default admin password: admin123 (BCrypt)

INSERT INTO sys_role (id, code, name, description) VALUES
(1, 'SUPER_ADMIN', '超级管理员', '拥有全部权限'),
(2, 'ADMIN', '管理员', '用户/应用/模型/文件管理'),
(3, 'OPERATOR', '运维员', '应用部署与运维'),
(4, 'VIEWER', '只读用户', '只读访问');

INSERT INTO sys_user (id, username, password, nickname, email, status) VALUES
(1, 'admin', '$2a$10$ZghT94EfeK/D7CGmKbcVh.3QSCHZqjNZJP9I7I4lVmCn9JjcRLGs.', '系统管理员', 'admin@agentpanel.local', 'enabled');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO sys_permission (id, code, name, type) VALUES
(1, 'system:user:read', '查看用户', 'api'),
(2, 'system:user:write', '管理用户', 'api'),
(3, 'system:role:read', '查看角色', 'api'),
(4, 'system:role:write', '管理角色', 'api'),
(5, 'system:menu:read', '查看菜单', 'api'),
(6, 'system:menu:write', '管理菜单', 'api'),
(7, 'app:read', '查看应用', 'api'),
(8, 'app:write', '管理应用', 'api'),
(9, 'app:deploy', '部署应用', 'api'),
(10, 'app:operate', '操作应用', 'api'),
(11, 'app:delete', '删除应用', 'api'),
(12, 'file:read', '查看文件', 'api'),
(13, 'file:write', '管理文件', 'api'),
(14, 'ai:read', '查看模型', 'api'),
(15, 'ai:write', '管理模型', 'api'),
(16, 'ai:chat', '对话调试', 'api'),
(17, 'audit:read', '查看审计', 'api');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(1, '仪表盘', '/dashboard', 'DashboardOutlined', './dashboard', NULL, 1, NULL, FALSE),
(2, '系统管理', '/system', 'SettingOutlined', NULL, NULL, 10, NULL, FALSE),
(3, '用户管理', '/system/user', 'UserOutlined', './system/user', 2, 1, 1, FALSE),
(4, '角色管理', '/system/role', 'TeamOutlined', './system/role', 2, 2, 3, FALSE),
(5, '菜单管理', '/system/menu', 'MenuOutlined', './system/menu', 2, 3, 5, FALSE),
(6, '应用中心', '/app', 'AppstoreOutlined', NULL, NULL, 2, NULL, FALSE),
(7, '应用列表', '/app/list', 'UnorderedListOutlined', './app/list', 6, 1, 7, FALSE),
(8, '文件管理', '/files', 'FolderOutlined', './files', NULL, 3, 12, FALSE),
(9, '模型网关', '/ai', 'RobotOutlined', NULL, NULL, 4, NULL, FALSE),
(10, '模型配置', '/ai/models', 'ApiOutlined', './ai/models', 9, 1, 14, FALSE),
(11, '对话调试', '/ai/playground', 'CommentOutlined', './ai/playground', 9, 2, 16, FALSE);

INSERT INTO agent_template (code, name, description, icon, image, default_tag, port_schema, env_schema, volume_schema, default_resources, builtin, doc_url) VALUES
('openclaw', 'OpenClaw', '网关型多通道 Agent 编排器', 'GatewayOutlined', 'ghcr.io/openclaw/openclaw', 'latest',
 '[{"name":"gateway","containerPort":18789,"protocol":"TCP","expose":true}]',
 '[{"key":"OPENROUTER_API_KEY","label":"OpenRouter API Key","required":false,"secret":true,"default":"","description":"OpenRouter 密钥"},{"key":"OLLAMA_BASE_URL","label":"Ollama 地址","required":false,"secret":false,"default":"http://host.docker.internal:11434","description":"本地 Ollama 服务地址"}]',
 '[{"name":"data","containerPath":"/home/node/.openclaw","required":true,"description":"OpenClaw 数据目录"}]',
 '{"cpu":"1","memory":"1Gi"}', TRUE, 'https://github.com/openclaw/openclaw'),

('hermes', 'Hermes Agent', '自我进化型推理 Agent', 'ExperimentOutlined', 'ghcr.io/nousresearch/hermes-agent', 'latest',
 '[{"name":"gateway","containerPort":3000,"protocol":"TCP","expose":true},{"name":"dashboard","containerPort":9119,"protocol":"TCP","expose":false}]',
 '[{"key":"OPENAI_API_KEY","label":"OpenAI API Key","required":false,"secret":true,"default":"","description":"OpenAI 兼容密钥"},{"key":"OPENAI_BASE_URL","label":"OpenAI Base URL","required":false,"secret":false,"default":"","description":"自定义 OpenAI 兼容端点"}]',
 '[{"name":"data","containerPath":"/opt/data","required":true,"description":"Hermes 数据目录"}]',
 '{"cpu":"2","memory":"2Gi"}', TRUE, 'https://github.com/NousResearch/hermes-agent'),

('openclaude', 'openclaude', 'Claude 系列 Agent 运行时（占位）', 'CodeOutlined', 'ghcr.io/example/openclaude', 'latest',
 '[{"name":"api","containerPort":8080,"protocol":"TCP","expose":true}]',
 '[{"key":"ANTHROPIC_API_KEY","label":"Anthropic API Key","required":false,"secret":true,"default":"","description":"Anthropic 密钥"}]',
 '[{"name":"data","containerPath":"/data","required":true,"description":"openclaude 数据目录"}]',
 '{"cpu":"1","memory":"1Gi"}', TRUE, NULL);

SELECT setval('sys_role_id_seq', (SELECT MAX(id) FROM sys_role));
SELECT setval('sys_user_id_seq', (SELECT MAX(id) FROM sys_user));
SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
