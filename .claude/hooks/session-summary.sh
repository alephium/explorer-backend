#!/usr/bin/env bash
# Stop hook: prints a summary of the session's git state.
REPO=/home/thomas/dev/explorer-backend

echo ""
echo "=== Session complete ==="

stat=$(git -C "$REPO" diff --stat HEAD 2>/dev/null)
staged=$(git -C "$REPO" diff --cached --stat 2>/dev/null)
untracked=$(git -C "$REPO" ls-files --others --exclude-standard 2>/dev/null)

if [[ -n "$stat" || -n "$staged" || -n "$untracked" ]]; then
  echo "Uncommitted changes detected:"
  [[ -n "$staged" ]]   && echo "  Staged:    $staged"
  [[ -n "$stat" ]]     && echo "  Unstaged:  $stat"
  [[ -n "$untracked" ]] && echo "  Untracked: $(echo "$untracked" | wc -l | tr -d ' ') file(s)"
  echo ""
  echo "Before opening a PR run:  make test-all"
  echo "To check API spec:        sbt \"tools/runMain org.alephium.tools.OpenApiUpdate\""
else
  echo "Working tree clean."
fi
