#!/usr/bin/env bash
# 拉取拓扑共享技能列表
# 用法: TOPOLOGY_ID=1 ./fetch_skills.sh
set -euo pipefail

TOPOLOGY_ID="${TOPOLOGY_ID:-1}"
SKILLS_API="${AGENTPANEL_SKILLS_API:-http://agent-panel:8080/api/skills?topologyId=${TOPOLOGY_ID}}"
API_KEY="${AGENTPANEL_API_KEY:?AGENTPANEL_API_KEY required}"

curl -sS "${SKILLS_API}" \
  -H "X-API-Key: ${API_KEY}" \
  | python -m json.tool 2>/dev/null || cat

echo
