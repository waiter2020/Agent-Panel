# Agent Panel 生产环境上线检查清单

本文档汇总将 Agent Panel 部署到生产环境前的安全与运维检查项。适用于 Docker Compose、裸 K8s 清单与 Helm Chart。

## 1. 密钥与凭证

- [ ] **JWT Secret**：`secrets.jwtSecret` / `AGENTPANEL_JWT_SECRET` 使用 ≥32 字符随机值，禁止默认值 `change-me-in-production`
- [ ] **应用加密密钥**：`secrets.appSecretKey` / `AGENTPANEL_APP_SECRET_KEY` 为 32 字节 hex，用于 env 密文存储
- [ ] **数据库密码**：`database.password` 独立强密码，与开发环境不同
- [ ] **MinIO 凭证**：`storage.accessKey` / `storage.secretKey` 已轮换
- [ ] **默认管理员**：修改 `admin` 初始密码；评估是否禁用或 MFA 保护超级管理员
- [ ] **API 密钥**：删除或轮换开发用 `apk_*` 密钥；为每个拓扑/集成方单独签发 scope 最小化密钥
- [ ] **密钥轮换计划**：文档化 JWT、API Key、DB、对象存储的轮换周期与责任人

## 2. TLS 与 Ingress

- [ ] **HTTPS 强制**：Ingress 配置 TLS（`ingress.tls.enabled: true` 或 `deploy/k8s/ingress-panel.yaml`）
- [ ] **cert-manager**：生产启用 `ingress.certManager.enabled: true` 并配置有效 `ClusterIssuer`（如 `letsencrypt-prod`）
- [ ] **HSTS**：在 Ingress Controller 层启用 `Strict-Transport-Security`（nginx annotation 或 Gateway API policy）
- [ ] **内部通信**：Postgres / MinIO 集群内访问；若跨节点明文传输，使用 Service Mesh 或 TLS
- [ ] **推理端点**：`panel.inferenceUrl` 指向 HTTPS 面板地址，避免 Agent 容器内 HTTP 明文传 API Key

## 3. 数据库与 pgvector

- [ ] **镜像**：使用 `pgvector/pgvector:pg16` 或受支持版本（Helm `postgres.image`）
- [ ] **扩展**：首次部署执行 `deploy/scripts/enable-pgvector.sql`（若未内置于迁移）
- [ ] **备份**：配置每日逻辑备份（`pg_dump`）与 WAL/快照；验证恢复演练
- [ ] **连接池**：生产设置合理 `max_connections` 与 Hikari 池大小
- [ ] **网络**：数据库 Service 仅集群内可达，禁止公网暴露 5432

## 4. 运行时与 Docker Socket 风险

- [ ] **Provider 选择**：生产优先 `runtimeProvider: k8s`，避免挂载 **docker.sock**
- [ ] **若必须使用 Docker**：限制宿主机访问、使用 rootless Docker 或专用节点；面板进程非 root
- [ ] **RBAC**：K8s 模式确认 `agentpanel-apps` 命名空间 Role 最小权限（仅 deployments/services/pods 等必需资源）；并绑定 ClusterRole `agent-panel-metrics-reader`（`metrics.k8s.io` + `nodes/proxy`），否则 CPU/内存与网络监控不可用
- [ ] **metrics-server**：集群已安装 metrics-server；`kubectl top pods -n agentpanel-apps` 可返回数据
- [ ] **镜像拉取**：Helm 配置 `image.pullSecrets`（渲染为 Pod `imagePullSecrets`）与私有镜像仓库扫描
- [ ] **资源限制**：为面板与 Agent 工作负载设置 `resources.requests/limits`

## 5. 多租户与访问控制

- [ ] **租户隔离**：非 SUPER_ADMIN 用户已分配正确 `tenant_id`；验证应用/拓扑列表按租户过滤
- [ ] **SUPER_ADMIN 账户**：数量最小化，操作纳入审计
- [ ] **API Key Scope**：推理密钥仅含 `ai:chat`、`memory:*`、`skill:read` 等必需 scope
- [ ] **审计日志**：保留策略与导出（`audit_log` 表）

## 6. 对象存储

- [ ] **Bucket 策略**：MinIO bucket 私有；仅通过预签名 URL 对外
- [ ] **技能文件**：校验上传大小与类型；定期清理孤立对象
- [ ] **外部 S3**：启用 SSE-S3 或 SSE-KMS

## 7. 监控与可观测性

- [ ] **健康检查**：Docker Compose 与 K8s 探针经由容器 HTTP 入口访问 `/actuator/health`、`/actuator/health/liveness`、`/actuator/health/readiness`，覆盖 nginx 到 Spring Boot 的完整入口链路
- [ ] **日志目录**：应用日志落盘至 `LOG_PATH`（默认 `/var/log/agent-panel`），文件为 `agent-panel.log` 与 `agent-panel-error.log`
- [ ] **日志卷**：Docker Compose 挂载 `panel-logs` 卷；K8s 挂载 `emptyDir` 或 PVC 至 `/var/log/agent-panel`
- [ ] **环境变量**：`LOG_PATH`、`LOG_LEVEL`、`SPRING_PROFILES_ACTIVE=prod` 已配置
- [ ] **集中收集**：同时采集容器 stdout 与文件日志（Loki/ELK）；排障时用响应头 `X-Request-Id` 关联请求
- [ ] **指标**：JVM、HTTP、DB 连接池、部署失败率告警
- [ ] **审计与安全事件**：`skill_reload_notify`、登录失败、API Key 403 异常频率

## 8. 网络与防火墙

- [ ] **入站**：仅 443（及必要 SSH 堡垒）对公网开放
- [ ] **出站**：Agent 容器仅允许访问面板、模型 API、必要 MCP 对等体
- [ ] **Webhook**：`AGENTPANEL_DELEGATION_WEBHOOK` 仅面板内网可达

## 9. 部署前冒烟测试

- [ ] `helm template` 或 `kubectl apply --dry-run=client` 无错误
- [ ] 登录、创建应用、部署拓扑、共享技能 notify-reload、Agent Hook 轮询
- [ ] `mvnw test` 与前端构建产物已打入镜像

## 10. 回滚与灾难恢复

- [ ] 保留上一版本镜像 tag 与 Helm revision
- [ ] 数据库迁移可回滚方案（Flyway repair 流程文档化）
- [ ] RTO/RPO 目标与 on-call 联系人

---

**相关文件**

- Helm：`deploy/helm/agent-panel/`
- 裸清单：`deploy/k8s/`
- Ingress TLS 示例：`deploy/k8s/ingress-panel.yaml`、`deploy/helm/agent-panel/templates/ingress.yaml`
- pgvector：`deploy/scripts/enable-pgvector.sql`
