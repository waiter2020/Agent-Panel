-- API key scope vocabulary (see ApiKeyScopeMatcher + README)
-- inference:  ai:chat           -> /v1/**
-- memory:      memory:read/write -> /api/memory/**
-- skill:       skill:read/write  -> /api/skills/**
-- delegation:  delegation:read/write -> /api/delegations/**

INSERT INTO sys_setting (key, value, description) VALUES
('api_key.scopes', '["ai:chat","memory:read","memory:write","skill:read","skill:write","delegation:read","delegation:write"]',
 'API 密钥可用 scope 列表（JSON）')
ON CONFLICT (key) DO UPDATE SET
    value = EXCLUDED.value,
    description = EXCLUDED.description,
    updated_at = NOW();
