#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repo_root/compose.yaml"
command_name="${1:-help}"

compose() { docker compose --project-directory "$repo_root" -f "$compose_file" "$@"; }

show_help() {
  cat <<'EOF'
Su dung: bash scripts/redis.sh <command>

  up      Khoi dong rieng Redis o detached mode.
  down    Dung va xoa rieng container Redis; giu named volume va PostgreSQL.
  status  Hien thi trang thai Redis.
  ready   Kiem tra PING da xac thuc.
  logs    Hien thi log Redis (khong follow vo han).
  cli     Mo Redis CLI da xac thuc trong container.
  info    Hien thi version, uptime, persistence va memory an toan.
  help    Hien thi huong dan nay.
EOF
}

if [[ "$command_name" == 'help' ]]; then show_help; exit 0; fi
case "$command_name" in up|down|status|ready|logs|cli|info) ;; *) echo "Command khong hop le: $command_name" >&2; show_help >&2; exit 1;; esac
command -v docker >/dev/null 2>&1 || { echo 'Khong tim thay Docker CLI.' >&2; exit 1; }
docker version --format '{{.Server.Version}}' >/dev/null 2>&1 || { echo 'Docker daemon khong san sang hoac khong the truy cap.' >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo 'Khong tim thay Docker Compose.' >&2; exit 1; }
[[ -f "$compose_file" ]] || { echo "Khong tim thay $compose_file" >&2; exit 1; }

case "$command_name" in
  up) compose up -d redis ;;
  down) compose stop redis && compose rm -f redis ;;
  status) compose ps redis ;;
  ready) compose exec -T redis redis-cli ping ;;
  logs) compose logs redis ;;
  cli) compose exec redis redis-cli ;;
  info) compose exec -T redis redis-cli info server; compose exec -T redis redis-cli info persistence; compose exec -T redis redis-cli info memory ;;
esac
