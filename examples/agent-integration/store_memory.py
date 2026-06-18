#!/usr/bin/env python3
"""最小共享记忆写入客户端（使用 AGENTPANEL_API_KEY）。"""
import json
import os
import sys
import urllib.error
import urllib.request

MEMORY_API = os.environ.get("AGENTPANEL_MEMORY_API", "http://agent-panel:8080/api/memory").rstrip("/")
API_KEY = os.environ.get("AGENTPANEL_API_KEY")
TOPOLOGY_ID = int(os.environ.get("TOPOLOGY_ID", "1"))
APP_ID = int(os.environ.get("APPLICATION_ID", "0")) or None


def store(key: str, content: str) -> dict:
    if not API_KEY:
        raise SystemExit("AGENTPANEL_API_KEY is required")
    body = {
        "key": key,
        "content": content,
        "scope": "topology",
        "topologyId": TOPOLOGY_ID,
    }
    if APP_ID:
        body["applicationId"] = APP_ID
    req = urllib.request.Request(
        MEMORY_API,
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "X-API-Key": API_KEY,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


if __name__ == "__main__":
    k = sys.argv[1] if len(sys.argv) > 1 else "demo-key"
    v = sys.argv[2] if len(sys.argv) > 2 else "hello from agent"
    try:
        print(json.dumps(store(k, v), ensure_ascii=False, indent=2))
    except urllib.error.HTTPError as e:
        print(e.read().decode(), file=sys.stderr)
        raise SystemExit(1)
