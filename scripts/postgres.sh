#!/usr/bin/env bash

set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$repo_root/compose.yaml"
command_name="${1:-help}"

compose() {
  docker compose --project-directory "$repo_root" -f "$compose_file" "$@"
}

show_help() {
  cat <<'EOF'
Su dung: bash scripts/postgres.sh <command>

  up      Khoi dong PostgreSQL o detached mode.
  down    Dung va xoa rieng container postgres; giu named volume va Redis.
  status  Hien thi trang thai service postgres.
  ready   Kiem tra pg_isready trong container.
  logs    Hien thi log cua postgres (khong follow vo han).
  psql    Mo psql ben trong container.
  help    Hien thi huong dan nay.
EOF
}

if [[ "$command_name" == 'help' ]]; then
  show_help
  exit 0
fi

case "$command_name" in
  up|down|status|ready|logs|psql) ;;
  *) echo "Command khong hop le: $command_name" >&2; show_help >&2; exit 1 ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo 'Khong tim thay Docker CLI.' >&2
  exit 1
fi
if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  echo 'Docker daemon khong san sang hoac khong the truy cap.' >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo 'Khong tim thay Docker Compose.' >&2
  exit 1
fi
if [[ ! -f "$compose_file" ]]; then
  echo "Khong tim thay $compose_file" >&2
  exit 1
fi

case "$command_name" in
  up) compose up -d postgres ;;
  down) compose stop postgres && compose rm -f postgres ;;
  status) compose ps postgres ;;
  ready) compose exec -T postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' ;;
  logs) compose logs postgres ;;
  psql) compose exec postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' ;;
esac
