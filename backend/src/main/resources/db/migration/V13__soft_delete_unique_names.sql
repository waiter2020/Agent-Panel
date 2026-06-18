-- Allow reusing names after soft delete: unique only among active rows
ALTER TABLE application DROP CONSTRAINT IF EXISTS application_name_key;
CREATE UNIQUE INDEX application_name_active_key ON application (name) WHERE deleted = false;

ALTER TABLE agent_topology DROP CONSTRAINT IF EXISTS agent_topology_name_key;
CREATE UNIQUE INDEX agent_topology_name_active_key ON agent_topology (name) WHERE deleted = false;
