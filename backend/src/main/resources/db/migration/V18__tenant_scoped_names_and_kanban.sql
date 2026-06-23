-- Per-tenant unique application and topology names among active rows
DROP INDEX IF EXISTS application_name_active_key;
CREATE UNIQUE INDEX application_name_active_key ON application (tenant_id, name) WHERE deleted = false;

DROP INDEX IF EXISTS agent_topology_name_active_key;
CREATE UNIQUE INDEX agent_topology_name_active_key ON agent_topology (tenant_id, name) WHERE deleted = false;

-- Add deploying column to the default tenant board
UPDATE app_task_column SET order_no = 3 WHERE board_id = 1 AND status_mapping = 'running' AND deleted = false;
UPDATE app_task_column SET order_no = 4 WHERE board_id = 1 AND status_mapping = 'stopped' AND deleted = false;
UPDATE app_task_column SET order_no = 5 WHERE board_id = 1 AND status_mapping = 'error' AND deleted = false;

INSERT INTO app_task_column (board_id, name, status_mapping, order_no, color)
SELECT 1, '部署中', 'deploying', 2, 'processing'
WHERE NOT EXISTS (
    SELECT 1 FROM app_task_column
    WHERE board_id = 1 AND status_mapping = 'deploying' AND deleted = false
);

-- Seed default kanban board for tenants that do not have one yet
INSERT INTO app_task_board (name, scope_type, tenant_id)
SELECT '应用运维看板', 'platform', t.id
FROM sys_tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM app_task_board b
    WHERE b.tenant_id = t.id AND b.deleted = false
);

-- Seed columns for newly created boards (excluding board id=1 which already has columns)
INSERT INTO app_task_column (board_id, name, status_mapping, order_no, color)
SELECT b.id, cols.name, cols.status_mapping, cols.order_no, cols.color
FROM app_task_board b
CROSS JOIN (VALUES
    ('待部署', 'created', 1, 'blue'),
    ('部署中', 'deploying', 2, 'processing'),
    ('运行中', 'running', 3, 'green'),
    ('已停止', 'stopped', 4, 'default'),
    ('异常', 'error', 5, 'red')
) AS cols(name, status_mapping, order_no, color)
WHERE b.deleted = false
  AND b.id <> 1
  AND NOT EXISTS (
      SELECT 1 FROM app_task_column c
      WHERE c.board_id = b.id AND c.deleted = false
  );
