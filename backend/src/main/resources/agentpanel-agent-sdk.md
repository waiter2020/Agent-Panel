# Agent Panel Agent SDK（精简版）

面向拓扑内 Agent 运行时的 API 速查。完整说明见项目根目录 `docs/agent-integration.md`。

## 环境变量（拓扑部署注入）

```
AGENTPANEL_SKILLS_API=http://agent-panel:8080/api/skills?topologyId={id}
AGENTPANEL_DELEGATION_WEBHOOK=http://agent-panel:8080/api/delegations/webhook
AGENTPANEL_MEMORY_API=http://agent-panel:8080/api/memory
AGENTPANEL_API_KEY={topology inference key}
OPENAI_BASE_URL=http://agent-panel:8080/v1
OPENAI_API_KEY={same as AGENTPANEL_API_KEY}
SHARED_SKILLS_JSON=[...]
```

## 认证

```
X-API-Key: $AGENTPANEL_API_KEY
```

## 端点

| 操作 | 请求 |
| --- | --- |
| 上报委派 | `POST $AGENTPANEL_DELEGATION_WEBHOOK?id={id}` + JSON body |
| 写入记忆 | `POST $AGENTPANEL_MEMORY_API` |
| 搜索记忆 | `GET $AGENTPANEL_MEMORY_API/search?q=...&topologyId=...` |
| 列出技能 | `GET $AGENTPANEL_SKILLS_API` |
| 技能热加载信号 | `POST /api/skills/{id}/notify-reload`（面板侧触发） |

## 示例脚本

见仓库 `examples/agent-integration/`：

- `report_delegation.sh`
- `store_memory.py`
- `fetch_skills.sh`
