CREATE TABLE sys_setting (
    id              BIGSERIAL PRIMARY KEY,
    key             VARCHAR(128) NOT NULL UNIQUE,
    value           TEXT,
    description     VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO sys_setting (key, value, description) VALUES
('panel.title', 'Agent Panel', '面板标题'),
('panel.version', '1.0.0', '面板版本');
