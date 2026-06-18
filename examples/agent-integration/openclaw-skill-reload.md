# OpenClaw / Hermes 技能热加载接入指南

面板在技能更新后可触发 `notify-reload`，Agent 需监听 reload 信号并重新拉取技能。本文说明如何在 OpenClaw 与 Hermes 中挂载 Hook。

## 前置条件

拓扑部署后，容器内应存在：

| 环境变量 | 说明 |
| --- | --- |
| `AGENTPANEL_SKILLS_API` | 技能列表，如 `http://agent-panel/api/skills?topologyId=1` |
| `AGENTPANEL_API_KEY` | 拓扑推理密钥，请求头 `X-API-Key` |
| `AGENTPANEL_TOPOLOGY_ID` | 可选，显式指定拓扑 ID |

面板侧触发：

```http
POST /api/skills/{id}/notify-reload
POST /api/topologies/{id}/notify-skills-reload
```

Agent 侧监听（二选一）：

```http
GET /api/skills/reload-events?topologyId=1&since=2026-06-18T00:00:00Z
GET /api/skills/reload-events/stream?topologyId=1   # SSE
```

## OpenClaw

1. 将 `examples/agent-integration/skill_reload_hook.py` 复制到 OpenClaw 数据卷，例如 `/home/node/.openclaw/hooks/skill_reload_hook.py`。
2. 在 `openclaw.json` 或启动脚本中注册后台任务（伪代码）：

```json
{
  "hooks": {
    "startup": ["python3 /home/node/.openclaw/hooks/skill_reload_hook.py"]
  }
}
```

3. 设置 `AGENTPANEL_RELOAD_MODE=sse` 可优先使用 SSE；网络不稳定时保持默认 `poll`。
4. 在 `on_reload` 回调中，将返回的技能元数据映射为 OpenClaw 本地技能目录或 MCP 工具注册。

## Hermes Agent

1. 在 Hermes 启动入口（`docker-entrypoint` 或 supervisor）增加 sidecar 进程：

```bash
python3 /opt/data/hooks/skill_reload_hook.py &
exec hermes-gateway ...
```

2. Hermes 自定义技能通常位于数据卷 `/opt/data/skills/`。收到 reload 后：
   - `GET AGENTPANEL_SKILLS_API` 获取列表
   - 对每个技能 `GET /api/skills/{id}/download` 拉取预签名 URL
   - 解压/写入本地并通知 Hermes 重新扫描

3. 若使用 Hermes Plugin API，在 `on_reload` 中调用插件热重载接口（以实际版本文档为准）。

## 验证

```bash
# 容器内
export AGENTPANEL_RELOAD_MODE=poll
python3 skill_reload_hook.py

# 另开终端，在面板 UI 或 API 触发 notify-reload，观察 Hook 日志输出
```

## 参考

- 通用集成文档：[docs/agent-integration.md](../../docs/agent-integration.md)
- 示例脚本目录：[examples/agent-integration/](./)
