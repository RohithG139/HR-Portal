#!/usr/bin/env bash
# PreToolUse hook: blocks dangerous bash commands before execution.
# Claude Code passes {"tool_input": {"command": "..."}} on stdin. Exit 2 blocks the command.

set -uo pipefail

# Pick a Python that really runs (on Windows "python3" can be the Microsoft Store stub)
PY_BIN=""
for c in python3 python py; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c "" >/dev/null 2>&1; then PY_BIN="$c"; break; fi
done
if [ -z "$PY_BIN" ]; then
  echo "block-dangerous: no Python found; skipping" >&2
  exit 0
fi

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | "$PY_BIN" -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_input',d).get('command',''))" 2>/dev/null || echo "")

[ -z "$COMMAND" ] && exit 0

DENY_PATTERNS=(
    "rm -rf /"
    "rm -rf \*"
    "chmod 777"
    "git push --force"
    "git push -f"
    "git reset --hard"
    "DROP TABLE"
    "DROP SCHEMA"
    "DROP DATABASE"
    "TRUNCATE TABLE"
    "dropdb"
    "flyway:clean"
    "docker volume rm"
    "docker volume prune"
    "docker system prune"
    "docker compose down -v"
    "docker compose down --volumes"
    "docker-compose down -v"
)

for PATTERN in "${DENY_PATTERNS[@]}"; do
    if echo "$COMMAND" | grep -qiE -- "$PATTERN" 2>/dev/null; then
        echo "[block-dangerous] BLOCKED: matches deny pattern: $PATTERN" >&2
        exit 2
    fi
done

exit 0
