-- OpenClaw 首次部署默认启动参数，避免 gateway 未配置时 exit 78 重启循环

UPDATE agent_template
SET env_schema = '[
  {"key":"OPENROUTER_API_KEY","label":"OpenRouter API Key","required":false,"secret":true,"default":"","description":"OpenRouter 密钥"},
  {"key":"OLLAMA_BASE_URL","label":"Ollama 地址","required":false,"secret":false,"default":"http://host.docker.internal:11434","description":"本地 Ollama 服务地址"},
  {"key":"OPENCLAW_GATEWAY_MODE","label":"Gateway Mode","required":false,"secret":false,"default":"local","description":"OpenClaw 网关模式，首次部署建议 local"},
  {"key":"OPENCLAW_ALLOW_UNCONFIGURED","label":"Allow Unconfigured","required":false,"secret":false,"default":"true","description":"允许未完成 openclaw setup 时启动"}
]'::jsonb,
    description = '网关型多通道 Agent 编排器。首次部署将自动注入 gateway.mode=local；也可通过数据卷文件上传配置。'
WHERE code = 'openclaw';
