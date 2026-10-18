#!/usr/bin/env bash
# Stop hook: quick compile / type check of the CubeWorks HR projects when Claude finishes a turn.
# Full tests (mvnw verify, npm test) run in the gates of /implement-feature, not here.

set -uo pipefail

# Project root = two levels above this script (.claude/hooks/)
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

ERRORS=0

run_check() {
    local LABEL="$1"; shift
    if ! "$@" 2>&1; then
        echo "[verify-output] FAILED: $LABEL" >&2
        ERRORS=$((ERRORS + 1))
    fi
}

# Backend: Java 21 / Spring Boot / Maven wrapper
if [ -f "$ROOT/hr-backend/pom.xml" ] && [ -f "$ROOT/hr-backend/mvnw" ]; then
    run_check "hr-backend compile" bash -c "cd '$ROOT/hr-backend' && ./mvnw -q -DskipTests compile"
fi

# Frontend: React + TypeScript (Vite)
if [ -f "$ROOT/hr-frontend/package.json" ] && [ -d "$ROOT/hr-frontend/node_modules" ]; then
    run_check "hr-frontend type check" bash -c "cd '$ROOT/hr-frontend' && npx tsc -b --pretty false"
fi

if [ "$ERRORS" -gt 0 ]; then
    echo "[verify-output] $ERRORS check(s) failed." >&2
    exit 1
fi

exit 0
