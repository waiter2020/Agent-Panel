-- P10: multi-tenant foundation + MCP endpoint metadata

CREATE TABLE sys_tenant (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO sys_tenant (id, name, code) VALUES (1, '默认租户', 'default');

ALTER TABLE application
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES sys_tenant(id);

ALTER TABLE agent_topology
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES sys_tenant(id);

CREATE INDEX idx_application_tenant ON application(tenant_id);
CREATE INDEX idx_agent_topology_tenant ON agent_topology(tenant_id);

ALTER TABLE mcp_endpoint
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
