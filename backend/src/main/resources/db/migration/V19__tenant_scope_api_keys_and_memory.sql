ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES sys_tenant(id);

CREATE INDEX IF NOT EXISTS idx_api_key_tenant ON api_key(tenant_id);

ALTER TABLE shared_memory
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE shared_memory m
SET tenant_id = COALESCE(
    (SELECT a.tenant_id FROM application a WHERE a.id = m.application_id),
    (SELECT t.tenant_id FROM agent_topology t WHERE t.id = m.topology_id),
    (SELECT u.tenant_id FROM sys_user u WHERE u.id = m.created_by),
    1
)
WHERE m.tenant_id IS NULL;

ALTER TABLE shared_memory
    ALTER COLUMN tenant_id SET DEFAULT 1,
    ALTER COLUMN tenant_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'shared_memory_tenant_id_fkey'
    ) THEN
        ALTER TABLE shared_memory
            ADD CONSTRAINT shared_memory_tenant_id_fkey
            FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shared_memory_tenant ON shared_memory(tenant_id);

UPDATE sys_menu SET order_no = 4 WHERE path = '/app/topology';
UPDATE sys_menu SET order_no = 5 WHERE path = '/app/memory';

INSERT INTO sys_menu (name, path, icon, component, parent_id, order_no, permission_id, hidden)
SELECT '运维看板', '/app/kanban', 'ProjectOutlined', './app/kanban', 6, 2, 7, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/app/kanban');

INSERT INTO sys_menu (name, path, icon, component, parent_id, order_no, permission_id, hidden)
SELECT '端口全景', '/app/ports', 'ApartmentOutlined', './app/ports', 6, 3, 7, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/app/ports');

SELECT setval('sys_menu_id_seq', (SELECT MAX(id) FROM sys_menu));
