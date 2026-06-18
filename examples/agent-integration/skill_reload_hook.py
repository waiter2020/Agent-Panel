#!/usr/bin/env python3
"""
Agent Panel 技能热加载 Hook 示例。

轮询面板审计事件或 SSE 流，在收到 reload 信号后重新拉取技能列表。
环境变量（拓扑部署时自动注入）：
  AGENTPANEL_SKILLS_API   - 技能列表 API，如 http://panel/api/skills?topologyId=1
  AGENTPANEL_API_KEY       - 拓扑推理密钥（X-API-Key）
  AGENTPANEL_TOPOLOGY_ID   - 可选，未设置时从 SKILLS_API 查询参数解析
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Any


def _panel_base() -> str:
    skills_api = os.environ.get("AGENTPANEL_SKILLS_API", "").strip()
    if not skills_api:
        return ""
    parsed = urllib.parse.urlparse(skills_api)
    return f"{parsed.scheme}://{parsed.netloc}"


def _topology_id() -> int | None:
    env_id = os.environ.get("AGENTPANEL_TOPOLOGY_ID", "").strip()
    if env_id.isdigit():
        return int(env_id)
    skills_api = os.environ.get("AGENTPANEL_SKILLS_API", "")
    query = urllib.parse.parse_qs(urllib.parse.urlparse(skills_api).query)
    values = query.get("topologyId") or query.get("topology_id")
    if values and values[0].isdigit():
        return int(values[0])
    return None


def _headers() -> dict[str, str]:
    headers = {"Accept": "application/json"}
    api_key = os.environ.get("AGENTPANEL_API_KEY", "").strip()
    if api_key:
        headers["X-API-Key"] = api_key
    return headers


def _get_json(url: str) -> Any:
    req = urllib.request.Request(url, headers=_headers())
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_skills() -> list[dict[str, Any]]:
    skills_api = os.environ.get("AGENTPANEL_SKILLS_API", "").strip()
    if not skills_api:
        return []
    payload = _get_json(skills_api)
    return payload.get("data") or []


def poll_reload_events(since_iso: str | None = None) -> list[dict[str, Any]]:
    topology_id = _topology_id()
    base = _panel_base()
    if topology_id is None or not base:
        return []
    url = f"{base}/api/skills/reload-events?topologyId={topology_id}"
    if since_iso:
        url += f"&since={urllib.parse.quote(since_iso)}"
    payload = _get_json(url)
    return payload.get("data") or []


def on_reload(skills: list[dict[str, Any]]) -> None:
    """在 Agent 运行时中替换此回调：将技能写入本地目录或注册到 OpenClaw/Hermes。"""
    print(f"[skill-reload] 已加载 {len(skills)} 个技能", flush=True)
    for skill in skills:
        print(f"  - {skill.get('name')} (id={skill.get('id')})", flush=True)


def watch_polling(interval_sec: float = 5.0) -> None:
    cursor = datetime.now(timezone.utc).isoformat()
    print(f"[skill-reload] 轮询模式启动，间隔 {interval_sec}s", flush=True)
    while True:
        try:
            events = poll_reload_events(cursor)
            if events:
                latest = max(
                    (e.get("notifiedAt") for e in events if e.get("notifiedAt")),
                    default=cursor,
                )
                cursor = latest
                on_reload(fetch_skills())
        except urllib.error.URLError as exc:
            print(f"[skill-reload] 轮询失败: {exc}", file=sys.stderr, flush=True)
        time.sleep(interval_sec)


def watch_sse() -> None:
    """简易 SSE 客户端（无额外依赖）。失败时回退到轮询。"""
    topology_id = _topology_id()
    base = _panel_base()
    if topology_id is None or not base:
        watch_polling()
        return
    url = f"{base}/api/skills/reload-events/stream?topologyId={topology_id}"
    req = urllib.request.Request(url, headers={**_headers(), "Accept": "text/event-stream"})
    print("[skill-reload] SSE 模式启动", flush=True)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            while True:
                line = resp.readline().decode("utf-8", errors="ignore").strip()
                if line.startswith("data:"):
                    on_reload(fetch_skills())
    except Exception as exc:
        print(f"[skill-reload] SSE 断开 ({exc})，回退轮询", file=sys.stderr, flush=True)
        watch_polling()


if __name__ == "__main__":
    mode = os.environ.get("AGENTPANEL_RELOAD_MODE", "poll").lower()
    if mode == "sse":
        watch_sse()
    else:
        watch_polling()
