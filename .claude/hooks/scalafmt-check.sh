#!/usr/bin/env bash
# PostToolUse hook: runs scalafmt --check on any .scala file just edited.
# Non-zero exit tells Claude there is a formatting violation to fix.
set -euo pipefail

file=$(printf '%s' "${CLAUDE_TOOL_INPUT:-{}}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('file_path',''))" 2>/dev/null || true)

# Only care about Scala sources.
[[ "$file" == *.scala ]] || exit 0

REPO=/home/thomas/dev/explorer-backend

if ! command -v scalafmt &>/dev/null; then
  # Fall back to sbt — slower but always available.
  echo "[scalafmt-check] scalafmt not on PATH; checking via sbt (slow)..."
  relative="${file#$REPO/}"
  result=$(cd "$REPO" && sbt "scalafmtCheck" 2>&1 | grep -E "(error|Reformatting|$relative)" | head -10 || true)
  if [[ -n "$result" ]]; then
    echo "[scalafmt-check] VIOLATION — run: sbt scalafmt"
    echo "$result"
    exit 1
  fi
  exit 0
fi

if scalafmt --check "$file" 2>/dev/null; then
  echo "[scalafmt-check] OK: $file"
else
  echo "[scalafmt-check] VIOLATION in $file"
  echo "Fix with:  sbt scalafmt"
  exit 1
fi
