#!/usr/bin/env bash

set -u
required_failure=0

result() { printf '[%s] %s: %s\n' "$1" "$2" "$3"; }
command_version() { "$@" 2>&1 | tr '\n' ' ' | sed 's/[[:space:]]*$//'; }

check_required() {
  local name="$1"
  shift
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    result FAIL "$name" 'Không tìm thấy công cụ.'
    required_failure=1
    return 1
  fi
  result PASS "$name" "$(command_version "$@")"
}

check_required Git git --version || true
java_version=''
javac_version=''
if command -v java >/dev/null 2>&1; then java_version="$(command_version java -version)"; result PASS 'Java runtime' "$java_version"; else result FAIL 'Java runtime' 'Không tìm thấy công cụ.'; required_failure=1; fi
if command -v javac >/dev/null 2>&1; then javac_version="$(command_version javac -version)"; result PASS 'Java compiler (javac)' "$javac_version"; else result FAIL 'Java compiler (javac)' 'Không tìm thấy công cụ.'; required_failure=1; fi
if [ -n "$java_version" ] && [ -n "$javac_version" ]; then
  if printf '%s\n%s\n' "$java_version" "$javac_version" | grep -Eq '(^|[^0-9])21([.[:space:]]|$)' && [ "$(printf '%s\n%s\n' "$java_version" "$javac_version" | grep -Ec '(^|[^0-9])21([.[:space:]]|$)')" -eq 2 ]; then
    result PASS 'Java 21' 'Java runtime và javac đều là phiên bản 21.'
  else
    result FAIL 'Java 21' 'Cần cả Java runtime và javac phiên bản 21.'
    required_failure=1
  fi
fi
check_required 'Node.js' node --version || true
check_required npm npm --version || true
check_required Docker docker --version || true
check_required 'Docker Compose' docker compose version || true
if command -v code >/dev/null 2>&1; then result PASS 'VS Code CLI' "$(command_version code --version)"; else result WARN 'VS Code CLI' 'Không tìm thấy; đây là công cụ tùy chọn.'; fi
exit "$required_failure"
