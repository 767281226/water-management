#!/usr/bin/env bash
# 宏禹水利管理平台 —— Linux Docker 部署管理脚本
#
#   ./deploy.sh init              初始化：生成 .env（随机数据库密码、JWT 密钥）、创建数据目录
#   ./deploy.sh up [--build]      启动（首次自动构建镜像；--build 强制重新构建）
#   ./deploy.sh down              停止并移除容器（数据保留）
#   ./deploy.sh restart [服务]    重启全部或指定服务（db / backend / web）
#   ./deploy.sh status            查看运行状态
#   ./deploy.sh logs [服务]       查看日志（默认 backend）
#   ./deploy.sh backup            备份数据库与上传资料到 BACKUP_DIR
#   ./deploy.sh restore <目录>    从备份恢复（会覆盖当前数据）
#   ./deploy.sh update            升级：先备份，再重新构建并滚动重启
#   ./deploy.sh cron [时:分]      安装每日自动备份（默认 02:30）
#   ./deploy.sh save-images       导出镜像（离线服务器部署用）
#   ./deploy.sh load-images <文件> 导入镜像
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")"
SCRIPT_DIR="$(pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
APP_UID=10001

info() { printf '\033[1;34m[信息]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[完成]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[注意]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[错误]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- 环境检查 ----------
detect_compose() {
  command -v docker >/dev/null 2>&1 || die "未安装 Docker，请先安装：https://docs.docker.com/engine/install/"
  docker info >/dev/null 2>&1 || die "无法连接 Docker 服务：请确认 Docker 已启动，且当前用户有权限（使用 sudo 或加入 docker 组）"
  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose)
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose)
  else
    die "未安装 Docker Compose（docker compose 插件或 docker-compose）"
  fi
}

dc() { "${COMPOSE[@]}" --env-file "$ENV_FILE" -f "$SCRIPT_DIR/docker-compose.yml" "$@"; }

# 读取 .env 中的配置（不 source，避免值中的空格/特殊字符被 shell 解释）
env_get() {
  local v
  v="$(grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2-)"
  printf '%s' "${v:-${2:-}}"
}

env_set() {
  if grep -qE "^$1=" "$ENV_FILE"; then
    sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$1" "$2" >>"$ENV_FILE"
  fi
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

abs_path() { case "$1" in /*) printf '%s' "$1" ;; *) printf '%s/%s' "$SCRIPT_DIR" "${1#./}" ;; esac; }

require_env() { [ -f "$ENV_FILE" ] || die "尚未初始化，请先执行：./deploy.sh init"; }

# ---------- 命令 ----------
cmd_init() {
  if [ -f "$ENV_FILE" ]; then
    info ".env 已存在，保留现有配置"
  else
    cp .env.example "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    info "已生成 .env"
  fi
  [ -n "$(env_get DB_PASSWORD)" ] || { env_set DB_PASSWORD "$(random_hex 16)"; info "已生成随机数据库密码"; }
  [ -n "$(env_get JWT_SECRET)" ] || { env_set JWT_SECRET "$(random_hex 32)"; info "已生成随机 JWT 密钥"; }

  local data backups
  data="$(abs_path "$(env_get DATA_DIR ./data)")"
  backups="$(abs_path "$(env_get BACKUP_DIR ./backups)")"
  mkdir -p "$data/postgres" "$data/docs" "$backups" "$SCRIPT_DIR/data/nas-empty"

  if [ "$(env_get NAS_TYPE none)" = "mount" ]; then
    local nas
    nas="$(env_get NAS_HOST_DIR)"
    [ -n "$nas" ] || die "NAS_TYPE=mount 时必须设置 NAS_HOST_DIR（宿主机上的网盘挂载点）"
    [ -d "$nas" ] || die "网盘目录不存在：$nas，请先在宿主机挂载网盘"
  fi
  ok "初始化完成。可按需修改 $ENV_FILE（端口、数据目录、网盘），然后执行 ./deploy.sh up"
}

image_exists() { docker image inspect "$1" >/dev/null 2>&1; }

build_images() {
  [ -d "$SCRIPT_DIR/../../backend" ] && [ -d "$SCRIPT_DIR/../../frontend" ] \
    || die "未找到源码目录（backend/、frontend/），无法构建镜像。离线部署请使用 load-images 导入镜像"
  info "构建镜像（首次需要下载依赖，可能需要几分钟）..."
  dc build --pull
}

# 上传目录由容器内的非 root 用户写入，用后端镜像修正属主（无需宿主机 sudo）
fix_permissions() {
  dc run --rm --no-deps --user root --entrypoint chown backend -R "$APP_UID:$APP_UID" /app/data/docs >/dev/null
}

wait_healthy() {
  local id status i
  info "等待服务启动（首次启动会自动创建数据库表）..."
  for i in $(seq 1 60); do
    id="$(dc ps -q backend)"
    status="$( [ -n "$id" ] && docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo starting)"
    case "$status" in
      healthy) return 0 ;;
      unhealthy|exited|dead) break ;;
    esac
    sleep 5
  done
  dc logs --tail 80 backend || true
  die "后端未能正常启动（状态：${status:-未知}），请查看上面的日志"
}

print_access() {
  local port ip
  port="$(env_get HTTP_PORT 80)"
  ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  ok "系统已启动：http://${ip:-服务器IP}$([ "$port" = 80 ] || printf ':%s' "$port")"
  echo "       首次部署默认管理员：admin / admin123（首次登录强制修改密码）"
}

cmd_up() {
  require_env
  cmd_init >/dev/null
  local tag
  tag="$(env_get APP_VERSION latest)"
  if [ "${1:-}" = "--build" ] || ! image_exists "hongyu-water/backend:$tag" || ! image_exists "hongyu-water/web:$tag"; then
    build_images
  fi
  fix_permissions
  dc up -d
  wait_healthy
  print_access
}

cmd_backup() {
  require_env
  local root dir keep
  root="$(abs_path "$(env_get BACKUP_DIR ./backups)")"
  keep="$(env_get BACKUP_KEEP_DAYS 30)"
  dir="$root/$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$dir"
  [ -n "$(dc ps -q db 2>/dev/null)" ] || die "数据库容器未运行，请先执行 ./deploy.sh up"

  info "备份数据库..."
  dc exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' >"$dir/db.dump"
  info "备份上传资料..."
  dc run --rm --no-deps -T --entrypoint tar backend -czf - -C /app/data docs >"$dir/docs.tar.gz"
  cp "$ENV_FILE" "$dir/env.backup" && chmod 600 "$dir/env.backup"

  [ -s "$dir/db.dump" ] || die "数据库备份为空，请检查"
  ok "备份完成：$dir（$(du -sh "$dir" | cut -f1)）"

  if [ "$keep" -gt 0 ] 2>/dev/null; then
    find "$root" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$keep" -exec rm -rf {} + || true
  fi
}

cmd_restore() {
  require_env
  local dir="${1:-}"
  [ -n "$dir" ] || die "用法：./deploy.sh restore <备份目录>"
  dir="$(abs_path "$dir")"
  [ -f "$dir/db.dump" ] || die "备份目录中没有 db.dump：$dir"
  if [ "${2:-}" != "-y" ]; then
    warn "恢复会覆盖当前数据库和上传资料！"
    read -r -p "确认从 $dir 恢复？输入 yes 继续：" answer
    [ "$answer" = "yes" ] || die "已取消"
  fi

  info "停止后端与前端..."
  dc stop web backend
  dc up -d db
  for _ in $(seq 1 30); do dc exec -T db sh -c 'pg_isready -U "$POSTGRES_USER"' >/dev/null 2>&1 && break; sleep 2; done

  info "恢复数据库..."
  dc exec -T db sh -c 'psql -q -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" -c "CREATE DATABASE \"$POSTGRES_DB\""'
  dc exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --role="$POSTGRES_USER"' <"$dir/db.dump"

  if [ -f "$dir/docs.tar.gz" ]; then
    info "恢复上传资料..."
    dc run --rm --no-deps -T --user root --entrypoint sh backend \
      -c "find /app/data/docs -mindepth 1 -delete && tar -xzf - -C /app/data && chown -R $APP_UID:$APP_UID /app/data/docs" <"$dir/docs.tar.gz"
  fi

  dc up -d
  wait_healthy
  ok "恢复完成"
}

cmd_update() {
  require_env
  if [ -n "$(dc ps -q db 2>/dev/null)" ]; then
    info "升级前先备份..."
    cmd_backup
  fi
  build_images
  fix_permissions
  # 数据库结构由 Flyway 在后端启动时自动升级
  dc up -d --remove-orphans
  wait_healthy
  docker image prune -f >/dev/null 2>&1 || true
  print_access
}

cmd_cron() {
  local at="${1:-02:30}" h m line
  h="${at%%:*}"; m="${at##*:}"
  line="$m $h * * * $SCRIPT_DIR/deploy.sh backup >> $SCRIPT_DIR/backup.log 2>&1"
  ( crontab -l 2>/dev/null | grep -vF "$SCRIPT_DIR/deploy.sh backup"; echo "$line" ) | crontab -
  ok "已安装每日 $at 自动备份（crontab -l 查看）"
}

cmd_save_images() {
  require_env
  local tag file
  tag="$(env_get APP_VERSION latest)"
  file="$SCRIPT_DIR/water-management-images-$tag.tar.gz"
  image_exists "hongyu-water/backend:$tag" || build_images
  docker pull postgres:17-alpine >/dev/null 2>&1 || true
  info "导出镜像到 $file ..."
  docker save "hongyu-water/backend:$tag" "hongyu-water/web:$tag" postgres:17-alpine | gzip >"$file"
  ok "导出完成。将 deploy/docker 目录与该文件复制到目标服务器后执行：./deploy.sh load-images $(basename "$file") && ./deploy.sh init && ./deploy.sh up"
}

cmd_load_images() {
  local file="${1:-}"
  [ -f "$file" ] || die "用法：./deploy.sh load-images <镜像文件.tar.gz>"
  gunzip -c "$file" | docker load
  ok "镜像导入完成"
}

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; }

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    help|-h|--help) usage; return ;;
  esac
  detect_compose
  case "$cmd" in
    init) cmd_init ;;
    up|start) cmd_up "$@" ;;
    down|stop) require_env; dc down ;;
    restart) require_env; dc restart "$@" ;;
    status|ps) require_env; dc ps ;;
    logs) require_env; dc logs -f --tail 200 "${@:-backend}" ;;
    backup) cmd_backup ;;
    restore) cmd_restore "$@" ;;
    update|upgrade) cmd_update ;;
    cron) cmd_cron "$@" ;;
    save-images) cmd_save_images ;;
    load-images) cmd_load_images "$@" ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
