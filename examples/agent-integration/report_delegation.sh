#!/usr/bin/env bash
# 通过 Webhook 上报委派完成状态
# 用法: ./report_delegation.sh <delegation_id> [status]
set -euo pipefail

DELEGATION_ID="${1:?delegation id required}"
STATUS="${2:-completed}"
WEBHOOK="${AGENTPANEL_DELEGATION_WEBHOOK:-http://agent-panel:8080/api/delegations/webhook}"
API_KEY="${AGENTPANEL_API_KEY:?AGENTPANEL_API_KEY required}"

curl -sS -X POST "${WEBHOOK}?id=${DELEGATION_ID}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{\"status\":\"${STATUS}\",\"result\":{\"summary\":\"reported from agent\"}}"

echo
