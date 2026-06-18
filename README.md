# Agent Panel

面向自托管 AI Agent 的统一管理面板。支持 OpenClaw、Hermes Agent、openclaude 等容器的托管部署、RBAC 权限、监控日志、MinIO 文件存储与 Spring AI 模型网关。

## 技术栈

- **前端**: umi max + antd 6 + ProComponents
- **后端**: Spring Boot 4 + Java 21 + JPA + Flyway + Spring Security JWT
- **数据库**: PostgreSQL 16
- **存储**: MinIO (S3 兼容)
- **编排**: Docker (docker-java) / Kubernetes (fabric8)

## 项目结构

```
agent-panel/
├── frontend/          # umi max 前端
├── backend/           # Spring Boot 后端
├── deploy/            # Dockerfile / docker-compose / k8s
└── docs/              # 设计文档
```

## 快速开始 (Docker Compose)

### 前置条件

- Docker & Docker Compose
- 可访问 Docker Engine (挂载 docker.sock)

> **安全提示**：Docker provider 模式下，面板容器需要以 root 用户运行并挂载 `/var/run/docker.sock`，这等同于宿主机 root 权限。仅限可信单机环境使用，请限制面板暴露面并开启鉴权。

### 启动

```bash
cd deploy
docker compose up -d --build
```

访问 http://localhost:8080

- 默认账号: `admin` / `admin123`
- MinIO 控制台: http://localhost:9001 (minioadmin / minioadmin)

Compose 编排三个服务：**panel**（nginx + Spring Boot）、**postgres**（pgvector/pg16）、**minio**（对象存储）。面板镜像仅包含应用本身，数据库与存储单独部署、独立持久化。

国内构建默认使用 DaoCloud 镜像加速与阿里云 Maven；海外构建可传 `--build-arg MIRROR_PROFILE=default --build-arg MAVEN_SETTINGS_FILE=deploy/settings-default.xml`。

### 本地开发

**后端** (需 PostgreSQL + MinIO):

```bash
cd backend
# 无需全局安装 Maven，使用项目自带的 Maven Wrapper
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

构建 JAR：

```bash
cd backend
./mvnw package -DskipTests
```

**前端**:

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发服务器默认代理 `/api` 到 `http://localhost:8080`。

生产构建（嵌入静态资源）：

```bash
cd frontend && pnpm build
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/
cd ../backend && ./mvnw package -DskipTests
```

## 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `AGENT_RUNTIME_PROVIDER` | 运行时 provider (`docker` / `k8s`) | docker |
| `DOCKER_HOST` | Docker socket 地址 | unix:///var/run/docker.sock |
| `AGENT_DATA_ROOT` | Agent 卷数据根目录（面板容器内路径） | /data/apps |
| `AGENT_HOST_DATA_ROOT` | Agent 卷在 Docker 宿主机上的根路径（通常自动探测，无需手动设置） | (自动探测) |
| `AGENT_DOCKER_RESPONSE_TIMEOUT` | Docker API 响应超时 | 300s |
| `AGENT_ACCESS_HOST` | Docker 模式应用访问地址主机名（概览页展示 host:port） | localhost |
| `K8S_ACCESS_HOST` | K8s 模式节点 IP 或 LB 地址（NodePort 访问展示） | localhost |
| `DB_HOST` | PostgreSQL 主机 | localhost |
| `STORAGE_ENDPOINT` | MinIO/S3 端点 | http://localhost:9000 |
| `JWT_SECRET` | JWT 签名密钥 | (开发默认值) |
| `PANEL_INFERENCE_URL` | 拓扑部署时注入成员应用的推理网关地址 | http://agent-panel:8080/v1 |
| `K8S_EXPOSE_VIA_INGRESS` | K8s 模式下为 Agent 应用创建 Ingress | false |
| `K8S_INGRESS_HOST` | Agent Ingress 主机后缀（`app-{id}.{host}`） | agentpanel.local |
| `K8S_INGRESS_TLS_SECRET` | Agent Ingress TLS Secret 名称 | (空) |
| `API_KEY_ROTATION_GRACE_DAYS` | API 密钥轮换宽限期（天） | 7 |
| `SPRING_AI_OPENAI_ENABLED` | 启用 OpenAI 嵌入模型 | false |
| `OPENAI_API_KEY` | OpenAI API 密钥（嵌入/推理） | (空) |
| `OPENAI_EMBEDDING_MODEL` | OpenAI 嵌入模型 | text-embedding-3-small |
| `SPRING_AI_OLLAMA_ENABLED` | 启用 Ollama 嵌入模型 | false |
| `OLLAMA_BASE_URL` | Ollama 服务地址 | http://localhost:11434 |
| `OLLAMA_EMBEDDING_MODEL` | Ollama 嵌入模型 | nomic-embed-text |
| `MEMORY_EMBEDDING_DIM` | pgvector 向量维度 | 1536 |

### Docker 部署说明

- 运行时镜像以 **root** 用户运行，以便访问挂载的 `docker.sock`
- Agent 数据卷默认挂载到 `AGENT_DATA_ROOT`（默认 `/data/apps`）
- SSE 日志/监控流通过短期 ticket 鉴权（`POST /api/auth/sse-ticket`），前端 EventSource 使用 `?token=` 参数传递

### API 密钥与 `/v1` 推理端点

`/v1/chat/completions` 支持两种认证方式：

1. **JWT**：登录用户需具备 `ai:chat` 权限
2. **API Key**：`Authorization: Bearer <key>` 或 `X-API-Key: <key>`

在 **系统管理 → API 密钥** 页面（`/system/apikey`）创建密钥。密钥**仅在创建时显示一次**，请立即复制保存。

可用 scope（按路径前缀校验）：

| Scope | 用途 | 路径前缀 |
|-------|------|----------|
| `ai:chat` | 推理网关 | `/v1/**` |
| `memory:read` / `memory:write` | 共享记忆 | `/api/memory/**` |
| `skill:read` / `skill:write` | 共享技能 | `/api/skills/**` |
| `delegation:read` / `delegation:write` | 委派追踪 | `/api/delegations/**` |

开发环境默认密钥见 Flyway `V5__api_key_audit_menu.sql`（`apk_agentpanel_dev_inference_key`，scope: `ai:chat`）。

> **生产环境务必轮换 API 密钥**：删除或禁用开发默认密钥，通过管理页面创建新密钥并妥善保管。密钥泄露等同于授予对应 scope 的 API 访问权限。

### 系统设置

管理员可在 **系统管理 → 系统设置**（`/system/settings`）查看和编辑面板配置项。`storage.*` 与 `runtime.*` 由环境变量注入，页面只读展示。

## Kubernetes 部署

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/rbac.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/postgres.yaml
kubectl apply -f deploy/k8s/minio.yaml
# 构建并推送镜像后
kubectl apply -f deploy/k8s/deployment.yaml
# 可选：启用 HTTPS Ingress（需先创建 TLS Secret）
kubectl apply -f deploy/k8s/ingress-panel.yaml
```

面板 Ingress 使用 `deploy/k8s/ingress-panel.yaml`，包含 TLS Secret 占位符。生产环境请：

1. 将 `agent-panel.example.com` 替换为实际域名
2. 创建 TLS Secret（或使用 cert-manager 自动签发）
3. 配置 DNS 指向 Ingress Controller

Agent 应用 Ingress（`K8S_EXPOSE_VIA_INGRESS=true`）会为每个 `expose: true` 的应用创建 `{app-name}.{K8S_INGRESS_HOST}` 路由。

K8s 模式下设置 `AGENT_RUNTIME_PROVIDER=k8s`，面板 ServiceAccount 具备 `agentpanel-apps` 命名空间的最小 RBAC。

K8s 模式下应用详情页的「数据卷文件」通过 fabric8 客户端在运行中 Pod 内读写挂载卷（`PodOperations#file` + exec），无需宿主机目录映射。

### K8s 应用访问地址

部署时，对 `expose: true` 的端口会自动创建 **NodePort** Service，并将分配的 NodePort 回写到应用 `ports.hostPort` 与 `accessUrls`。

请设置 `K8S_ACCESS_HOST` 为集群节点 IP 或负载均衡地址，应用详情页将展示 `host:nodePort` 形式的访问地址。

若使用 ClusterIP + `kubectl port-forward`，可手动访问：

```bash
kubectl port-forward -n agentpanel-apps svc/<app-name> <local-port>:<container-port>
```

## 故障排查

| 现象 | 可能原因 | 处理建议 |
|------|----------|----------|
| `mvn` 命令不存在 | 未安装全局 Maven | 使用 `backend/mvnw`（或 Windows 下 `mvnw.cmd`） |
| Docker Compose 启动失败 | Docker Desktop 未运行或 `docker.sock` 不可访问 | 确认 Docker 服务已启动；Linux 上将当前用户加入 `docker` 组 |
| K8s 数据卷文件列表为空 | Pod 未运行或 RBAC 不足 | 确认应用已部署且 Pod 为 Running；检查 `deploy/k8s/rbac.yaml` 已 apply |
| 面板无法创建 Agent 容器 | `AGENT_DATA_ROOT` 目录权限不足 | 确保数据目录可写，Compose 模式下卷已正确挂载 |
| K8s 访问地址为空 | 未设置 `K8S_ACCESS_HOST` 或端口未 expose | 确认端口 schema 中 `expose: true`；设置节点 IP |

## 生产环境检查清单

- [ ] 修改默认管理员密码
- [ ] 轮换 `JWT_SECRET`、`APP_SECRET_KEY`、数据库密码
- [ ] 删除或禁用开发默认 API 密钥（`apk_agentpanel_dev_inference_key`）
- [ ] 通过 API 密钥页面创建生产密钥，禁用 `latest` 镜像标签，使用固定版本
- [ ] 配置 HTTPS Ingress 与 TLS 证书（`deploy/k8s/ingress-panel.yaml`）
- [ ] 设置 `PANEL_INFERENCE_URL` 为集群内可访问的面板 `/v1` 地址
- [ ] 限制 Docker socket 挂载面（仅可信环境）
- [ ] 配置备份策略（PostgreSQL + MinIO + Agent 数据卷）

## 协同拓扑（P9 阶段 A）

在 **应用中心 → 协同拓扑**（`/app/topology`）可创建协同组：

1. 新建拓扑，指定 Docker 内部网络名（默认 `agentpanel-net`）
2. 添加成员应用并分配角色（gateway / worker）
3. 一键部署：所有成员加入同一网络，自动注入 `OPENAI_BASE_URL` / `OPENAI_API_BASE` 与 `OPENAI_API_KEY`

集群内互访地址（Docker）：`http://app-{id}:{containerPort}`（同网络 DNS 不可用时使用容器名 `app-{id}`）。

K8s 模式：同 namespace 下 Service DNS 为 `app-{id}.agentpanel-apps.svc.cluster.local`。

## 协同拓扑（P9 阶段 B）

阶段 B 在阶段 A 基础上增加拓扑编排画布、节点链路与 MCP 注册发现：

### 拓扑链路（agent_link）

- 在拓扑详情画布中，gateway → worker 节点可建立 **HTTP** 或 **MCP** 链路
- 部署时按链路向源节点（通常为 gateway）注入对等 URL 环境变量，例如：
  - `HERMES_URL=http://app-{workerId}:3000`（Docker）
  - `HERMES_URL=http://app-{workerId}.agentpanel-apps.svc.cluster.local:3000`（K8s）
  - MCP 链路：`MCP_{TEMPLATE}_URL`，优先使用已注册的 MCP 端点 URL
- API：`GET/POST /api/topologies/{id}/links`、`DELETE /api/topologies/{id}/links/{linkId}`

### MCP 注册中心（mcp_endpoint）

- 在 **应用详情 → MCP 端点** 标签页注册 MCP Server URL
- API：`GET/POST/PUT/DELETE /api/mcp-endpoints`，`POST /api/mcp-endpoints/{id}/discover`（MVP 桩实现，返回模拟工具列表）
- 拓扑 MCP 链路部署时自动引用已注册端点

### 部署增强

- 部署完成后展示成员外部访问地址、集群内互访地址与注入环境变量摘要
- 推理 API 密钥轮换后，拓扑详情提示 **轮换密钥并重部署**（`POST /api/topologies/{id}/redeploy`）


## 协同拓扑（P9 阶段 C）

阶段 C 增加跨 Agent 共享记忆、委派追踪、技能文件共享与真实 MCP 工具发现：

### 共享记忆（shared_memory + pgvector）

- Flyway `V9` 启用 `vector` 扩展，表 `shared_memory` 支持 global / topology / app 三级 scope
- `SharedMemoryService`：有 `EmbeddingModel` 时写入向量并做余弦相似度检索；否则回退关键词搜索
- API：`POST /api/memory`、`GET /api/memory/search?q=`、`GET /api/memory`、`DELETE /api/memory/{id}`
- 权限：`memory:read`、`memory:write`
- 前端：**应用中心 → 共享记忆**（`/app/memory`）

配置嵌入维度（默认 1536）：

```yaml
agent.panel.memory.embedding-dimensions: 1536
```

启用 Spring AI 嵌入（OpenAI 示例）：

```yaml
spring.ai.openai.enabled: true
spring.ai.openai.api-key: ${OPENAI_API_KEY}
```

Ollama 本地嵌入（无需 OpenAI 密钥）：

```yaml
spring.ai.ollama.enabled: true
spring.ai.ollama.base-url: http://localhost:11434
spring.ai.ollama.embedding.options.model: nomic-embed-text
```

启动时面板会日志输出 `EmbeddingModel` 是否可用。无模型时记忆仍可存储，检索回退关键词匹配。

> Docker Compose 已切换为 `pgvector/pgvector:pg16` 镜像。已有 `postgres:16-alpine` 数据卷见下方升级说明。

#### 从 postgres:16 升级到 pgvector

若已有 `postgres-data` 卷（原 `postgres:16-alpine`），**无需删除数据**，在切换镜像后执行：

```bash
# 1. 停止面板，保留 postgres 卷
cd deploy
docker compose stop panel

# 2. 使用 pgvector 镜像重启 postgres（docker-compose.yml 已更新）
docker compose up -d postgres

# 3. 在已有库上启用扩展
docker compose exec -T postgres psql -U agentpanel -d agentpanel < scripts/enable-pgvector.sql

# 4. 启动面板（Flyway V9 会创建 shared_memory 等表）
docker compose up -d panel
```

脚本路径：`deploy/scripts/enable-pgvector.sql`。全新部署可跳过此步骤。

### 子智能体委派追踪（delegation_trace）

- 平台侧记录 API：`POST /api/delegations`、`GET /api/delegations?topologyId=`
- 状态更新：`PATCH /api/delegations/{id}`（status / result / completed_at）
- **Webhook**（Agent 运行时上报）：`POST /api/delegations/webhook?id={delegationId}`
  - 认证：API 密钥 scope `delegation:write`（`X-API-Key` 或 `Authorization: Bearer <key>`）
  - 请求体示例：`{"status":"completed","result":{"summary":"done"},"completedAt":"2026-06-18T12:00:00Z"}`
- 拓扑详情抽屉 **委派追踪** 标签页展示 Timeline
- 权限：`delegation:read`、`delegation:write`

### 共享技能（shared_skill + MinIO）

- 技能文本与可选文件（S3 key 存 `file_path`）按拓扑共享
- API：`/api/skills` CRUD + `GET /api/skills/{id}/download` 预签名下载
- **拓扑部署注入**：向所有成员写入
  - `AGENTPANEL_SKILLS_API=http://agent-panel:8080/api/skills?topologyId={id}`
  - `SHARED_SKILLS_JSON`：技能名称/路径 JSON 数组
- 拓扑详情 **共享技能** 标签页：列表、上传、下载；部署摘要展示注入技能列表
- 权限：`skill:read`、`skill:write`

### MCP 真实发现

- `McpEndpointService.discover()`：5s 超时，依次尝试 `GET {url}/tools`、根路径 JSON、JSON-RPC `tools/list`、**SSE**（`Accept: text/event-stream`，路径 `/sse`、`/mcp/sse`）
- 不可达时回退桩工具列表；结果写入 `mcp_endpoint.tools`，传输类型写入 `mcp_endpoint.metadata.transport`

### Agent 运行时集成（P10）

- 集成文档：[docs/agent-integration.md](docs/agent-integration.md)
- 示例脚本：`examples/agent-integration/`（委派 webhook、记忆写入、技能拉取）
- 拓扑部署额外注入：`AGENTPANEL_DELEGATION_WEBHOOK`、`AGENTPANEL_MEMORY_API`、`AGENTPANEL_API_KEY`
- 技能热加载：`POST /api/skills/{id}/notify-reload`、拓扑级 `POST /api/topologies/{id}/notify-skills-reload`
- Agent 监听：`GET /api/skills/reload-events?topologyId=`（轮询）或 `/api/skills/reload-events/stream`（SSE）
- Hook 示例：`examples/agent-integration/skill_reload_hook.py`、`openclaw-skill-reload.md`

### Helm Chart 部署

```bash
# 渲染模板验证（需 helm 3）
helm template agent-panel deploy/helm/agent-panel --namespace agentpanel

# 安装：自动创建 agentpanel 与 agentpanel-apps 命名空间
helm install agent-panel deploy/helm/agent-panel \
  --namespace agentpanel --create-namespace \
  --set image.repository=your-registry/agent-panel \
  --set image.tag=latest \
  --set ingress.host=panel.example.com \
  --set appsNamespace.create=true \
  --set ingress.certManager.enabled=true \
  --set ingress.certManager.clusterIssuer=letsencrypt-prod

# 使用外部 Postgres / MinIO
helm install agent-panel deploy/helm/agent-panel \
  --set postgres.enabled=false \
  --set database.external=true \
  --set database.host=postgres.example.com \
  --set minio.enabled=false \
  --set storage.external=true \
  --set storage.endpoint=http://minio.example.com:9000
```

Chart 路径：`deploy/helm/agent-panel/`（含可选内嵌 postgres/minio、`agentpanel-apps` 工作负载命名空间与 RBAC）。

生产上线前请参阅：[deploy/production-checklist.md](deploy/production-checklist.md)。

## 功能模块

1. **认证与 RBAC** - JWT 登录、用户/角色/菜单/权限、审计日志、API 密钥管理
2. **应用中心** - 内置模板、应用 CRUD、部署/启停/重启/删除、部署后端口回写与访问地址展示
3. **监控与日志** - SSE 推送 CPU/内存 stats 与日志流（支持 `since` 参数）
4. **文件存储** - MinIO 对象存储浏览/预签名上传下载
5. **模型网关** - LLM Provider/模型 CRUD、Playground 对话调试、OpenAI 兼容 `/v1` 端点
6. **系统设置** - 面板配置管理页面与 `GET/PUT /api/settings` API
7. **协同拓扑** - 多 Agent 同网部署、共享推理网关、拓扑画布与链路注入（P9 阶段 A/B/C）
8. **MCP 注册中心** - 按应用登记 MCP 端点与工具发现（P9 阶段 B/C）
9. **共享记忆** - pgvector 向量检索与跨 Agent 记忆 API（P9 阶段 C）
10. **委派追踪与共享技能** - 子智能体委派可视化、拓扑级技能文件共享（P9 阶段 C）

## 路线图（未来）

- **P12+**：租户配额、MCP SSE 双向会话、Agent 推送通道增强

## 许可证

Apache 2.0
