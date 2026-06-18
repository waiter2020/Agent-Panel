-- P9 Stage A: Agent collaboration topology + pinned image tags + API key rotation support

ALTER TABLE api_key ADD COLUMN IF NOT EXISTS deprecated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE agent_topology (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL UNIQUE,
    description         TEXT,
    network_name        VARCHAR(128) NOT NULL DEFAULT 'agentpanel-net',
    status              VARCHAR(32) NOT NULL DEFAULT 'draft',
    owner_id            BIGINT NOT NULL REFERENCES sys_user(id),
    inference_api_key_id BIGINT REFERENCES api_key(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE agent_topology_node (
    id              BIGSERIAL PRIMARY KEY,
    topology_id     BIGINT NOT NULL REFERENCES agent_topology(id) ON DELETE CASCADE,
    application_id  BIGINT NOT NULL REFERENCES application(id),
    role            VARCHAR(32) NOT NULL DEFAULT 'worker',
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (topology_id, application_id)
);

CREATE INDEX idx_topology_node_topology ON agent_topology_node(topology_id);
CREATE INDEX idx_topology_node_app ON agent_topology_node(application_id);

-- Pin template image tags (avoid floating :latest in production)
UPDATE agent_template SET default_tag = 'v1.0.0' WHERE code = 'openclaw';
UPDATE agent_template SET default_tag = 'v0.1.0' WHERE code = 'hermes';
UPDATE agent_template SET default_tag = 'v1.0.0' WHERE code = 'openclaude';

INSERT INTO sys_permission (id, code, name, type) VALUES
(22, 'topology:read', '查看协同拓扑', 'api'),
(23, 'topology:write', '管理协同拓扑', 'api'),
(24, 'topology:deploy', '部署协同拓扑', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 22), (1, 23), (1, 24),
(2, 22), (2, 23), (2, 24),
(3, 22), (3, 24);

INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(15, '协同拓扑', '/app/topology', 'ShareAltOutlined', './app/topology', 6, 2, 22, FALSE);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
