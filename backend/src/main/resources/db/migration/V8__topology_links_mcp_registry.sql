-- P9 Stage B: topology links + MCP endpoint registry

CREATE TABLE agent_link (
    id              BIGSERIAL PRIMARY KEY,
    topology_id     BIGINT NOT NULL REFERENCES agent_topology(id) ON DELETE CASCADE,
    from_node_id    BIGINT NOT NULL REFERENCES agent_topology_node(id) ON DELETE CASCADE,
    to_node_id      BIGINT NOT NULL REFERENCES agent_topology_node(id) ON DELETE CASCADE,
    protocol        VARCHAR(16) NOT NULL DEFAULT 'http',
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (topology_id, from_node_id, to_node_id, protocol)
);

CREATE INDEX idx_agent_link_topology ON agent_link(topology_id);
CREATE INDEX idx_agent_link_from ON agent_link(from_node_id);
CREATE INDEX idx_agent_link_to ON agent_link(to_node_id);

CREATE TABLE mcp_endpoint (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    url             VARCHAR(512) NOT NULL,
    tools           JSONB NOT NULL DEFAULT '[]',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    discovered_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mcp_endpoint_app ON mcp_endpoint(application_id);
