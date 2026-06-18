# Agent 与 Agent Panel 集成指南

本文说明运行在拓扑内的 Agent（OpenClaw、Hermes、openclaude 等）如何通过面板 API 上报委派、读写共享记忆、拉取共享技能。

## 拓扑部署时注入的环境变量

拓扑 **部署** 后，面板会向每个成员应用写入以下变量（与 `OPENAI_BASE_URL` / `OPENAI_API_KEY` 一并注入）：

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `AGENTPANEL_SKILLS_API` | 共享技能列表 API | `http://agent-panel:8080/api/skills?topologyId=1` |
| `SHARED_SKILLS_JSON` | 技能名称/路径 JSON 快照 | `[{"id":1,"name":"search","filePath":"skills/1/search/SKILL.md"}]` |
| `AGENTPANEL_DELEGATION_WEBHOOK` | 委派状态上报 Webhook 基址 | `http://agent-panel:8080/api/delegations/webhook` |
| `AGENTPANEL_MEMORY_API` | 共享记忆 API 基址 | `http://agent-panel:8080/api/memory` |
| `AGENTPANEL_API_KEY` | 拓扑推理密钥（含 memory/delegation/skill scope） | `apk_...`（仅部署时显示一次，已加密存入应用 env） |
| `OPENAI_BASE_URL` | 面板模型网关 | `http://agent-panel:8080/v1` |
| `OPENAI_API_KEY` | 同上推理密钥 | `apk_...` |

> Docker Compose 网络内服务名通常为 `agent-panel` 或 `panel`；K8s 集群内为 `http://agent-panel.agentpanel.svc.cluster.local`。

## 认证方式

Agent 调用面板 API 时使用拓扑推理密钥：

```http
X-API-Key: <AGENTPANEL_API_KEY>
```

或：

```http
Authorization: Bearer <AGENTPANEL_API_KEY>
```

所需 scope（拓扑密钥已包含）：

| API | 方法 | Scope |
| --- | --- | --- |
| `/api/delegations/webhook` | POST | `delegation:write` |
| `/api/memory` | POST | `memory:write` |
| `/api/memory/search` | GET | `memory:read` |
| `/api/skills` | GET | `skill:read` |

## 1. 委派状态上报（Webhook）

创建委派记录（可由父 Agent 或面板 UI 预先创建）后，子 Agent 通过 Webhook 更新状态：

```http
POST /api/delegations/webhook?id={delegationId}
Content-Type: application/json
X-API-Key: ${AGENTPANEL_API_KEY}

{
  "status": "completed",
  "result": { "summary": "任务完成" },
  "completedAt": "2026-06-18T12:00:00Z"
}
```

Shell 示例见 `examples/agent-integration/report_delegation.sh`。

## 2. 共享记忆

**写入：**

```http
POST /api/memory
Content-Type: application/json
X-API-Key: ${AGENTPANEL_API_KEY}

{
  "key": "user-preference",
  "content": "用户偏好深色主题",
  "scope": "topology",
  "topologyId": 1,
  "applicationId": 100
}
```

**语义搜索：**

```http
GET /api/memory/search?q=用户偏好&topologyId=1&limit=10
X-API-Key: ${AGENTPANEL_API_KEY}
```

Python 示例见 `examples/agent-integration/store_memory.py`。

## 3. 拉取共享技能

```http
GET /api/skills?topologyId=1
X-API-Key: ${AGENTPANEL_API_KEY}
```

下载技能文件：

```http
GET /api/skills/{id}/download
```

Shell 示例见 `examples/agent-integration/fetch_skills.sh`。

## 4. 技能热加载通知

面板更新技能后，可调用 `POST /api/skills/{id}/notify-reload` 或拓扑级 `POST /api/topologies/{id}/notify-skills-reload` 记录审计事件。Agent 应在自定义 Hook 中监听该信号（或定期轮询 `AGENTPANEL_SKILLS_API`）并重新拉取技能。

### OpenClaw / Hermes 自定义 Hook 示例

在 Agent 启动脚本或 `hooks/` 目录中读取环境变量：

```python
import os, requests

PANEL = os.environ.get("AGENTPANEL_MEMORY_API", "").rstrip("/memory")
KEY = os.environ.get("AGENTPANEL_API_KEY", "")
WEBHOOK = os.environ.get("AGENTPANEL_DELEGATION_WEBHOOK", "")
SKILLS = os.environ.get("AGENTPANEL_SKILLS_API", "")

def report_delegation(delegation_id: int, status: str, result: dict):
    requests.post(
        f"{WEBHOOK}?id={delegation_id}",
        headers={"X-API-Key": KEY},
        json={"status": status, "result": result},
        timeout=10,
    )

def reload_skills():
    if not SKILLS:
        return []
    r = requests.get(SKILLS, headers={"X-API-Key": KEY}, timeout=10)
    r.raise_for_status()
    return r.json()["data"]
```

## 5. 更多参考

- 后端内嵌 SDK 说明：`backend/src/main/resources/agentpanel-agent-sdk.md`
- 示例脚本目录：`examples/agent-integration/`
