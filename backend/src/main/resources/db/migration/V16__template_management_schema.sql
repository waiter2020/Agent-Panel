ALTER TABLE agent_template
    ADD COLUMN IF NOT EXISTS management_schema JSONB NOT NULL DEFAULT '{}';

UPDATE agent_template SET management_schema = '{
  "tabs": [
    {"key": "overview", "label": "概览"},
    {"key": "env", "label": "环境变量"},
    {"key": "webConsole", "label": "Gateway", "consoleKey": "gateway"},
    {"key": "console", "label": "终端", "permission": "app:terminal"},
    {"key": "files", "label": "数据卷", "volumeRefs": ["data"]},
    {"key": "monitor", "label": "监控"},
    {"key": "logs", "label": "日志"},
    {"key": "typePanel", "label": "OpenClaw", "panelKey": "openclaw"}
  ],
  "webConsoles": [
    {"key": "gateway", "portRef": "gateway", "title": "OpenClaw Gateway", "embed": "iframe"}
  ],
  "fileProfiles": [
    {"volume": "data", "label": "OpenClaw 数据", "editableExtensions": [".json", ".yaml", ".yml", ".md"], "criticalPaths": ["openclaw.json"]}
  ],
  "capabilities": ["terminal", "files", "monitor", "logs", "webConsole", "topology"],
  "healthCheck": {"portRef": "gateway", "path": "/"}
}'::jsonb WHERE code = 'openclaw';

UPDATE agent_template SET management_schema = '{
  "tabs": [
    {"key": "overview", "label": "概览"},
    {"key": "env", "label": "环境变量"},
    {"key": "webConsole", "label": "Dashboard", "consoleKey": "dashboard"},
    {"key": "webConsole", "label": "Gateway", "consoleKey": "gateway"},
    {"key": "skills", "label": "Skills", "permission": "skill:read"},
    {"key": "mcp", "label": "MCP 端点"},
    {"key": "console", "label": "终端", "permission": "app:terminal"},
    {"key": "files", "label": "数据卷", "volumeRefs": ["data"]},
    {"key": "monitor", "label": "监控"},
    {"key": "logs", "label": "日志"},
    {"key": "typePanel", "label": "Hermes", "panelKey": "hermes"}
  ],
  "webConsoles": [
    {"key": "dashboard", "portRef": "dashboard", "title": "Hermes Dashboard", "embed": "iframe", "internalOnly": true},
    {"key": "gateway", "portRef": "gateway", "title": "Hermes Gateway", "embed": "iframe"}
  ],
  "fileProfiles": [
    {"volume": "data", "label": "Hermes 数据", "editableExtensions": [".json", ".yaml", ".yml", ".md", ".txt"], "criticalPaths": []}
  ],
  "capabilities": ["terminal", "files", "monitor", "logs", "webConsole", "skills", "mcp", "topology"],
  "healthCheck": {"portRef": "gateway", "path": "/"}
}'::jsonb WHERE code = 'hermes';

UPDATE agent_template SET management_schema = '{
  "tabs": [
    {"key": "overview", "label": "概览"},
    {"key": "env", "label": "环境变量"},
    {"key": "typePanel", "label": "API", "panelKey": "openclaude"},
    {"key": "console", "label": "终端", "permission": "app:terminal"},
    {"key": "files", "label": "数据卷", "volumeRefs": ["data"]},
    {"key": "monitor", "label": "监控"},
    {"key": "logs", "label": "日志"}
  ],
  "webConsoles": [
    {"key": "api", "portRef": "api", "title": "openclaude API", "embed": "iframe"}
  ],
  "fileProfiles": [
    {"volume": "data", "label": "openclaude 数据", "editableExtensions": [".json", ".yaml", ".yml", ".md"], "criticalPaths": []}
  ],
  "capabilities": ["terminal", "files", "monitor", "logs", "webConsole"],
  "healthCheck": {"portRef": "api", "path": "/health"}
}'::jsonb WHERE code = 'openclaude';
