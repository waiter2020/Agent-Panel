-- Agent Panel schema initialization

CREATE TABLE sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    nickname        VARCHAR(128),
    email           VARCHAR(128),
    phone           VARCHAR(32),
    avatar          VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'enabled',
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE sys_role (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'enabled',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE sys_permission (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(16) NOT NULL DEFAULT 'api',
    parent_id       BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE sys_menu (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    path            VARCHAR(256),
    icon            VARCHAR(64),
    component       VARCHAR(256),
    parent_id       BIGINT,
    order_no        INT NOT NULL DEFAULT 0,
    permission_id   BIGINT REFERENCES sys_permission(id),
    hidden          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE sys_user_role (
    user_id         BIGINT NOT NULL REFERENCES sys_user(id),
    role_id         BIGINT NOT NULL REFERENCES sys_role(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id         BIGINT NOT NULL REFERENCES sys_role(id),
    permission_id   BIGINT NOT NULL REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_refresh_token (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES sys_user(id),
    token           VARCHAR(512) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    username        VARCHAR(64),
    action          VARCHAR(64) NOT NULL,
    resource_type   VARCHAR(64),
    resource_id     VARCHAR(64),
    detail          JSONB,
    ip              VARCHAR(64),
    result          VARCHAR(16) NOT NULL DEFAULT 'success',
    at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_template (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(128) NOT NULL,
    description         TEXT,
    icon                VARCHAR(64),
    image               VARCHAR(512) NOT NULL,
    default_tag         VARCHAR(128) NOT NULL,
    port_schema         JSONB NOT NULL DEFAULT '[]',
    env_schema          JSONB NOT NULL DEFAULT '[]',
    volume_schema       JSONB NOT NULL DEFAULT '[]',
    default_resources   JSONB NOT NULL DEFAULT '{}',
    builtin             BOOLEAN NOT NULL DEFAULT TRUE,
    doc_url             VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE application (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL UNIQUE,
    template_id         BIGINT NOT NULL REFERENCES agent_template(id),
    owner_id            BIGINT NOT NULL REFERENCES sys_user(id),
    image               VARCHAR(512),
    tag                 VARCHAR(128),
    status              VARCHAR(32) NOT NULL DEFAULT 'created',
    ports               JSONB NOT NULL DEFAULT '[]',
    resources           JSONB NOT NULL DEFAULT '{}',
    volumes             JSONB NOT NULL DEFAULT '[]',
    replicas            INT NOT NULL DEFAULT 1,
    runtime_provider    VARCHAR(16),
    remark              TEXT,
    runtime_ref         VARCHAR(256),
    runtime_namespace   VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE app_env (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    key             VARCHAR(256) NOT NULL,
    value           TEXT,
    is_secret       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (application_id, key)
);

CREATE TABLE app_deployment (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    provider        VARCHAR(16) NOT NULL,
    ref             VARCHAR(256),
    namespace       VARCHAR(128),
    image_used      VARCHAR(512),
    status          VARCHAR(32) NOT NULL,
    message         TEXT,
    started_at      TIMESTAMPTZ,
    stopped_at      TIMESTAMPTZ,
    spec_snapshot   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE llm_provider (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    base_url        VARCHAR(512),
    api_key         TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    config          JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE llm_model (
    id              BIGSERIAL PRIMARY KEY,
    provider_id     BIGINT NOT NULL REFERENCES llm_provider(id) ON DELETE CASCADE,
    model           VARCHAR(128) NOT NULL,
    label           VARCHAR(128),
    capabilities    JSONB NOT NULL DEFAULT '["chat"]',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (provider_id, model)
);

CREATE INDEX idx_application_status ON application(status);
CREATE INDEX idx_application_owner ON application(owner_id);
CREATE INDEX idx_audit_log_at ON audit_log(at DESC);
CREATE INDEX idx_app_deployment_app ON app_deployment(application_id);
