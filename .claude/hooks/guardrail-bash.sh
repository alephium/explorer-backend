#!/usr/bin/env bash
# PreToolUse guardrail for Bash tool calls.
# Exit 2 to block the command and surface the message to Claude.
set -euo pipefail

cmd=$(printf '%s' "${CLAUDE_TOOL_INPUT:-{}}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('command',''))" 2>/dev/null || true)

# Patterns that are never safe to run autonomously in this repo.
declare -a FORBIDDEN=(
  "git push --force"
  "git push -f"
  "git push origin master"
  "git reset --hard"
  "git clean -f"
  "DROP TABLE"
  "DROP DATABASE"
  "TRUNCATE"
  "rm -rf /"
)

for pattern in "${FORBIDDEN[@]}"; do
  if echo "$cmd" | grep -qi "$pattern"; then
    echo "GUARDRAIL BLOCKED: command matches forbidden pattern \"$pattern\"."
    echo "This is disallowed by CLAUDE.md invariants."
    echo "If this is genuinely needed, ask the user to run it manually."
    exit 2
  fi
done
