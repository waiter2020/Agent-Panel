# syntax=docker.m.daocloud.io/docker/dockerfile:1.7
#
# 国内默认：DaoCloud 基础镜像 + 清华 apk + 阿里云 Maven；pnpm 默认官方源（见 NPM_REGISTRY）。
# PostgreSQL / MinIO 等中间件请通过 docker-compose 或外部服务单独部署。
# 海外构建示例：
#   docker build --build-arg MIRROR_PROFILE=default \
#     --build-arg NODE_IMAGE=node:20-alpine \
#     --build-arg MAVEN_IMAGE=maven:3.9-eclipse-temurin-21 \
#     --build-arg JRE_IMAGE=eclipse-temurin:21-jre-alpine \
#     --build-arg MAVEN_SETTINGS_FILE=deploy/settings-default.xml \
#     -f deploy/Dockerfile .

ARG DOCKER_HUB_MIRROR=docker.m.daocloud.io
ARG NODE_IMAGE=${DOCKER_HUB_MIRROR}/library/node:20-alpine
ARG MAVEN_IMAGE=${DOCKER_HUB_MIRROR}/library/maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=${DOCKER_HUB_MIRROR}/library/eclipse-temurin:21-jre-alpine
ARG MIRROR_PROFILE=cn
ARG ALPINE_MIRROR_HOST=mirrors.tuna.tsinghua.edu.cn
ARG DEBIAN_MIRROR_HOST=mirrors.aliyun.com
ARG NPM_REGISTRY=https://registry.npmjs.org
ARG MAVEN_SETTINGS_FILE=deploy/settings.xml
ARG MAVEN_THREADS=1C

# ---------- 前端构建（pnpm store 缓存加速 install）----------
FROM ${NODE_IMAGE} AS frontend-builder
ARG MIRROR_PROFILE
ARG NPM_REGISTRY
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9 --activate
COPY frontend/package.json frontend/pnpm-lock.yaml ./
ENV PNPM_HOME=/root/.local/share/pnpm \
    PATH=/root/.local/share/pnpm:$PATH \
    NPM_CONFIG_REGISTRY=${NPM_REGISTRY}
RUN --mount=type=cache,id=agentpanel-pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY frontend/ .
RUN pnpm build

# ---------- 后端构建（Maven 依赖缓存）----------
FROM ${MAVEN_IMAGE} AS backend-builder
ARG MAVEN_SETTINGS_FILE
ARG MIRROR_PROFILE
ARG DEBIAN_MIRROR_HOST
ARG MAVEN_THREADS
WORKDIR /app
RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
        sed -i "s|deb.debian.org|${DEBIAN_MIRROR_HOST}|g; s|security.debian.org|${DEBIAN_MIRROR_HOST}|g" \
          /etc/apt/sources.list.d/debian.sources; \
      elif [ -f /etc/apt/sources.list ]; then \
        sed -i "s|archive.ubuntu.com|${DEBIAN_MIRROR_HOST}|g; s|security.ubuntu.com|${DEBIAN_MIRROR_HOST}|g" \
          /etc/apt/sources.list; \
        sed -i "s|deb.debian.org|${DEBIAN_MIRROR_HOST}|g" /etc/apt/sources.list 2>/dev/null || true; \
      fi; \
    fi
COPY ${MAVEN_SETTINGS_FILE} /usr/share/maven/ref/settings.xml
COPY backend/pom.xml .
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
RUN --mount=type=cache,id=agentpanel-maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml -T${MAVEN_THREADS} dependency:go-offline -B -q
COPY backend/src ./src
RUN --mount=type=cache,id=agentpanel-maven-repo,target=/root/.m2 \
    mvn -s /usr/share/maven/ref/settings.xml -T${MAVEN_THREADS} clean package -DskipTests -B -q

# ---------- Runtime：nginx + Java（中间件外置）----------
FROM ${JRE_IMAGE}

LABEL maintainer="Agent Panel Team" \
      description="Agent Panel - Self-hosted AI Agent management platform" \
      version="1.0.0"

ARG MIRROR_PROFILE
ARG ALPINE_MIRROR_HOST
WORKDIR /app

RUN if [ "$MIRROR_PROFILE" = "cn" ]; then \
      ver="$(cut -d. -f1,2 </etc/alpine-release)"; \
      printf '%s\n' \
        "https://${ALPINE_MIRROR_HOST}/alpine/v${ver}/main" \
        "https://${ALPINE_MIRROR_HOST}/alpine/v${ver}/community" \
        > /etc/apk/repositories; \
    fi && \
    apk add --no-cache nginx curl && \
    ln -sf /dev/stdout /var/log/nginx/access.log && \
    ln -sf /dev/stderr /var/log/nginx/error.log && \
    mkdir -p /run/nginx /app/data/apps /var/log/agent-panel

ENV JAVA_OPTS="-Xmx1024m -Xms256m" \
    AGENT_DATA_ROOT=/app/data/apps \
    LOG_PATH=/var/log/agent-panel \
    SPRING_PROFILES_ACTIVE=prod

VOLUME /var/log/agent-panel

COPY --from=backend-builder /app/target/agent-panel-backend-*.jar /app/app.jar
COPY --from=frontend-builder /app/dist /usr/share/nginx/html
COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/entrypoint.sh"]
