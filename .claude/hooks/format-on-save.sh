#!/usr/bin/env bash
# PostToolUse hook: formats a file after Edit/Write/NotebookEdit (Java, TypeScript/JS, CSS, JSON).
# Claude Code passes tool input as JSON on stdin. Formatting never blocks: missing tools are skipped.

set -uo pipefail

# Pick a Python that really runs (on Windows "python3" can be the Microsoft Store stub)
PY_BIN=""
for c in python3 python py; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c "" >/dev/null 2>&1; then PY_BIN="$c"; break; fi
done
if [ -z "$PY_BIN" ]; then
  echo "format-on-save: no Python found; skipping" >&2
  exit 0
fi

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | "$PY_BIN" -c "import json,sys; d=json.load(sys.stdin); print(d.get('tool_input',d).get('file_path',''))" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0
[ ! -f "$FILE_PATH" ] && exit 0

EXT="${FILE_PATH##*.}"

case "$EXT" in
    java)
        if command -v google-java-format &>/dev/null; then
            google-java-format --replace "$FILE_PATH" 2>/dev/null || true
        fi
        ;;
    ts|tsx|js|jsx|css|json)
        # Only frontend files; never touch docs or engine definitions
        case "$FILE_PATH" in
            *hr-frontend*)
                if [ -x "hr-frontend/node_modules/.bin/prettier" ]; then
                    hr-frontend/node_modules/.bin/prettier --write "$FILE_PATH" --log-level error 2>/dev/null || true
                elif command -v prettier &>/dev/null; then
                    prettier --write "$FILE_PATH" --log-level error 2>/dev/null || true
                fi
                ;;
        esac
        ;;
esac

exit 0
