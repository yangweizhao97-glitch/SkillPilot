#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_PID=""
FRONTEND_PID=""
CLEANED_UP=0

log() {
  printf '[start-dev] %s\n' "$*"
}

java_major_version() {
  "$1/bin/java" -version 2>&1 | awk -F'[".]' '/version/ { print ($2 == "1" ? $3 : $2); exit }'
}

node_major_version() {
  node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/'
}

select_java_home() {
  local candidate=""
  local major=""
  local candidates=(
    "${JAVA_HOME:-}"
    "/opt/homebrew/opt/openjdk@21"
    "/opt/homebrew/opt/openjdk"
    "/usr/local/opt/openjdk@21"
    "/usr/local/opt/openjdk"
  )

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    candidate="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    candidates+=("$candidate")
  fi

  for candidate in "${candidates[@]}"; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]]; then
      major="$(java_major_version "$candidate")"
      if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= 21 )); then
        export JAVA_HOME="$candidate"
        export PATH="$JAVA_HOME/bin:$PATH"
        return 0
      fi
    fi
  done

  log "未找到 JDK 21 或更高版本。请设置 JAVA_HOME 后重试。"
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log "缺少命令：$1"
    exit 1
  fi
}

require_node_version() {
  local major
  major="$(node_major_version)"
  if [[ ! "$major" =~ ^[0-9]+$ ]] || (( major < 22 )); then
    log "Node.js 版本需要 22 或更高；当前为 $(node -v 2>/dev/null || echo unknown)。"
    exit 1
  fi
}

ensure_port_available() {
  local port="$1"
  local service="$2"
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    log "$service 端口 $port 已被占用，请先停止占用进程。"
    exit 1
  fi
}

wait_for_container_healthy() {
  local container="$1"
  local label="$2"
  local status=""

  for _ in {1..60}; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      log "$label 已就绪。"
      return 0
    fi
    sleep 1
  done

  log "$label 启动超时，最后状态：${status:-unknown}"
  docker logs --tail 80 "$container" 2>/dev/null || true
  exit 1
}

cleanup() {
  local status=$?
  if (( CLEANED_UP == 1 )); then
    return
  fi
  CLEANED_UP=1
  trap - EXIT INT TERM

  log "正在停止前后端..."
  [[ -n "$FRONTEND_PID" ]] && kill "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  [[ -n "$FRONTEND_PID" ]] && wait "$FRONTEND_PID" 2>/dev/null || true
  [[ -n "$BACKEND_PID" ]] && wait "$BACKEND_PID" 2>/dev/null || true
  log "前后端已停止；PostgreSQL 和 Redis 容器继续运行。"
  exit "$status"
}

cd "$ROOT_DIR"

require_command docker
require_command npm
select_java_home
require_node_version

if ! docker info >/dev/null 2>&1; then
  log "Docker daemon 未运行，请先启动 Docker Desktop。"
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  log "Docker Compose 不可用，请确认 Docker Desktop 已安装并启用 compose 插件。"
  exit 1
fi

if [[ ! -f .env ]]; then
  log "未找到 .env，正从 .env.example 创建。"
  cp .env.example .env
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${JWT_SECRET:-}" || "${JWT_SECRET:-}" == change-me* || ${#JWT_SECRET} -lt 32 ]]; then
  if command -v openssl >/dev/null 2>&1; then
    JWT_SECRET="$(openssl rand -hex 32)"
  else
    JWT_SECRET="local-dev-only-$RANDOM-$RANDOM-$(date +%s)-$$"
  fi
  export JWT_SECRET
  log "JWT_SECRET 未配置有效值，已为本次开发会话生成临时密钥。"
fi

ensure_port_available "${SERVER_PORT:-8080}" "后端"
ensure_port_available "5173" "前端"

log "使用 Java $(java_major_version "$JAVA_HOME")：$JAVA_HOME"
log "使用 Node.js $(node -v)"
log "启动 PostgreSQL 和 Redis..."
docker compose up -d postgres redis
wait_for_container_healthy career-agent-postgres "PostgreSQL"
wait_for_container_healthy career-agent-redis "Redis"

if [[ ! -d frontend/node_modules ]]; then
  log "首次运行，安装前端依赖..."
  (cd frontend && npm ci)
fi

trap cleanup EXIT INT TERM

log "启动后端：http://localhost:${SERVER_PORT:-8080}"
(exec ./mvnw spring-boot:run) &
BACKEND_PID=$!

log "启动前端：http://localhost:5173"
(cd frontend && exec npm run dev) &
FRONTEND_PID=$!

log "前后端正在运行，按 Ctrl+C 一起停止。"

while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done

if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
  wait "$BACKEND_PID" || true
  log "后端进程已退出。"
else
  wait "$FRONTEND_PID" || true
  log "前端进程已退出。"
fi

exit 1
