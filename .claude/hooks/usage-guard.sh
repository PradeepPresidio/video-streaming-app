#!/usr/bin/env bash
# PostToolUse hook: watches plan usage recorded by statusline.sh.
#   >= HANDOFF_SOFT_PCT (95): tells Claude to stop and run the handoff procedure (guidelines.md §8).
#   >= HANDOFF_HARD_PCT (99): commits + pushes everything itself and stops the session.
set -u
cat >/dev/null   # hook input not needed

PROJ="${CLAUDE_PROJECT_DIR:-$(pwd)}"
STATE="$PROJ/.claude/state"
USAGE="$STATE/usage.json"
SOFT="${HANDOFF_SOFT_PCT:-95}"
HARD="${HANDOFF_HARD_PCT:-99}"
HOOKS="$PROJ/.claude/hooks"

stop_session() {
  jq -n --arg r "$1" '{continue: false, stopReason: $r}'
  exit 0
}

# Handoff already committed: do not let this session start new work.
if [ -f "$STATE/handoff_done" ]; then
  stop_session "Handoff committed and pushed. This session is finished; the next session will take over from HANDOFF.md after the usage limit resets."
fi

[ -f "$USAGE" ] || exit 0
now=$(date +%s)

# Highest usage among windows that have not reset yet; resume time = latest reset among windows over SOFT.
read -r PCT RESUME < <(jq -r --argjson now "$now" --argjson soft "$SOFT" '
  [ .five_hour, .seven_day ]
  | map(select(. != null and .used_percentage != null and .resets_at != null and .resets_at > $now))
  | if length == 0 then "0 0"
    else (map(.used_percentage) | max) as $p
       | ([ .[] | select(.used_percentage >= $soft) | .resets_at ] | max // 0) as $r
       | "\($p) \($r)"
    end' "$USAGE" 2>/dev/null || echo "0 0")

ge() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a >= b) }'; }

ge "$PCT" "$SOFT" || exit 0
[ "${RESUME:-0}" -gt 0 ] 2>/dev/null && echo "$RESUME" > "$STATE/resume_at"

if ge "$PCT" "$HARD"; then
  bash "$HOOKS/handoff-commit.sh" hard >/dev/null 2>&1
  stop_session "Plan usage at ${PCT}% (hard limit ${HARD}%). Work was auto-committed and pushed with a [HANDOFF] marker; the next session will take over after the limit resets."
fi

if [ ! -f "$STATE/handoff_requested" ]; then
  touch "$STATE/handoff_requested"
  MSG="USAGE LIMIT GUARD: plan usage is at ${PCT}% (threshold ${SOFT}%). Stop feature work NOW and run the handoff procedure in guidelines.md section 8:
1. Leave the tree coherent: finish or revert only the edit in progress. Start nothing new.
2. Write HANDOFF.md from the template in guidelines.md 8.2 with 'Status: PENDING'. Be specific: the next session has none of your context.
3. Update PROGRESS.md.
4. Run: bash .claude/hooks/handoff-commit.sh soft
5. Reply 'Handoff complete' and stop. Do not run further tools."
else
  MSG="USAGE LIMIT GUARD (reminder): usage ${PCT}%. Finish the handoff (HANDOFF.md, PROGRESS.md, then 'bash .claude/hooks/handoff-commit.sh soft') and stop."
fi

jq -n --arg c "$MSG" '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: $c}}'
