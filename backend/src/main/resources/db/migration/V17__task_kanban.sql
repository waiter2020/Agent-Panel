CREATE TABLE app_task_board (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    scope_type      VARCHAR(32) NOT NULL DEFAULT 'platform',
    scope_ref       VARCHAR(64),
    tenant_id       BIGINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE app_task_column (
    id              BIGSERIAL PRIMARY KEY,
    board_id        BIGINT NOT NULL REFERENCES app_task_board(id),
    name            VARCHAR(64) NOT NULL,
    status_mapping  VARCHAR(32),
    order_no        INT NOT NULL DEFAULT 0,
    color           VARCHAR(16),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE app_task (
    id              BIGSERIAL PRIMARY KEY,
    board_id        BIGINT NOT NULL REFERENCES app_task_board(id),
    column_id       BIGINT NOT NULL REFERENCES app_task_column(id),
    application_id  BIGINT REFERENCES application(id),
    title           VARCHAR(256) NOT NULL,
    description     TEXT,
    priority        VARCHAR(16) NOT NULL DEFAULT 'normal',
    order_no        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_app_task_board_tenant ON app_task_board(tenant_id) WHERE deleted = FALSE;
CREATE INDEX idx_app_task_column_board ON app_task_column(board_id) WHERE deleted = FALSE;
CREATE INDEX idx_app_task_column ON app_task(column_id) WHERE deleted = FALSE;
CREATE INDEX idx_app_task_app ON app_task(application_id) WHERE deleted = FALSE;

INSERT INTO app_task_board (id, name, scope_type, tenant_id) VALUES (1, '应用运维看板', 'platform', 1);

INSERT INTO app_task_column (id, board_id, name, status_mapping, order_no, color) VALUES
(1, 1, '待部署', 'created', 1, 'blue'),
(2, 1, '运行中', 'running', 2, 'green'),
(3, 1, '已停止', 'stopped', 3, 'default'),
(4, 1, '异常', 'error', 4, 'red');

SELECT setval('app_task_board_id_seq', (SELECT MAX(id) FROM app_task_board));
SELECT setval('app_task_column_id_seq', (SELECT MAX(id) FROM app_task_column));
