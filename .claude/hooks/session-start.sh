#!/usr/bin/env bash
# SessionStart hook: on a fresh start, clears the previous session's handoff flags and,
# if HANDOFF.md is pending, injects it so the new session takes over with full KT.
set -u
INPUT=$(cat)
SOURCE=$(printf '%s' "$INPUT" | jq -r '.source // "startup"' 2>/dev/null)
PROJ="${CLAUDE_PROJECT_DIR:-$(pwd)}"
STATE="$PROJ/.claude/state"
mkdir -p "$STATE"

if [ "$SOURCE" = "startup" ]; then
  rm -f "$STATE/handoff_requested" "$STATE/handoff_done" "$STATE/resume_at"
fi

if [ -f "$PROJ/HANDOFF.md" ] && grep -q '^Status: PENDING' "$PROJ/HANDOFF.md"; then
  CTX="TAKEOVER: a previous Claude Code session paused near the plan usage limit and handed off to you. Follow guidelines.md section 8.3 (takeover). HANDOFF.md:

$(cat "$PROJ/HANDOFF.md")"
  jq -n --arg c "$CTX" '{hookSpecificOutput: {hookEventName: "SessionStart", additionalContext: $c}}'
fi
exit 0
