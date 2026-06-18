-- P9 Stage C: shared memory (pgvector), delegation trace, shared skills, menus & permissions

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE shared_memory (
    id              BIGSERIAL PRIMARY KEY,
    topology_id     BIGINT REFERENCES agent_topology(id) ON DELETE CASCADE,
    application_id  BIGINT REFERENCES application(id) ON DELETE CASCADE,
    scope           VARCHAR(16) NOT NULL DEFAULT 'global',
    key             VARCHAR(256) NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(1536),
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT REFERENCES sys_user(id)
);

CREATE INDEX idx_shared_memory_scope ON shared_memory(scope);
CREATE INDEX idx_shared_memory_topology ON shared_memory(topology_id);
CREATE INDEX idx_shared_memory_app ON shared_memory(application_id);
CREATE INDEX idx_shared_memory_embedding ON shared_memory USING hnsw (embedding vector_cosine_ops);

CREATE TABLE shared_skill (
    id              BIGSERIAL PRIMARY KEY,
    topology_id     BIGINT NOT NULL REFERENCES agent_topology(id) ON DELETE CASCADE,
    application_id  BIGINT REFERENCES application(id) ON DELETE SET NULL,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    content         TEXT,
    file_path       VARCHAR(512),
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (topology_id, name)
);

CREATE INDEX idx_shared_skill_topology ON shared_skill(topology_id);

CREATE TABLE delegation_trace (
    id              BIGSERIAL PRIMARY KEY,
    topology_id     BIGINT NOT NULL REFERENCES agent_topology(id) ON DELETE CASCADE,
    parent_app_id   BIGINT NOT NULL REFERENCES application(id),
    child_app_id    BIGINT NOT NULL REFERENCES application(id),
    task_summary    TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'running',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    result          JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_delegation_topology ON delegation_trace(topology_id);
CREATE INDEX idx_delegation_started ON delegation_trace(started_at DESC);

INSERT INTO sys_permission (id, code, name, type) VALUES
(25, 'memory:read', '查看共享记忆', 'api'),
(26, 'memory:write', '管理共享记忆', 'api'),
(27, 'skill:read', '查看共享技能', 'api'),
(28, 'skill:write', '管理共享技能', 'api'),
(29, 'delegation:read', '查看委派追踪', 'api'),
(30, 'delegation:write', '记录委派追踪', 'api');

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 25), (1, 26), (1, 27), (1, 28), (1, 29), (1, 30),
(2, 25), (2, 26), (2, 27), (2, 28), (2, 29), (2, 30),
(3, 25), (3, 29);

INSERT INTO sys_menu (id, name, path, icon, component, parent_id, order_no, permission_id, hidden) VALUES
(16, '共享记忆', '/app/memory', 'DatabaseOutlined', './app/memory', 6, 3, 25, FALSE);

SELECT setval('sys_permission_id_seq', (SELECT MAX(id) FROM sys_permission));
SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
