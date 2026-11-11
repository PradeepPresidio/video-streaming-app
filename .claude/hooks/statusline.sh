#!/usr/bin/env bash
# Status line: shows context + plan usage, and records plan usage to
# .claude/state/usage.json so the usage-guard hook can act on it.
# rate_limits is only sent for claude.ai Pro/Max subscribers, after the first API response.
input=$(cat)

PROJ=$(printf '%s' "$input" | jq -r '.workspace.project_dir // .cwd // empty' 2>/dev/null)
[ -n "$PROJ" ] || PROJ=$(pwd)
STATE="$PROJ/.claude/state"
mkdir -p "$STATE"

if printf '%s' "$input" | jq -e '.rate_limits.five_hour or .rate_limits.seven_day' >/dev/null 2>&1; then
  printf '%s' "$input" \
    | jq -c '{five_hour: .rate_limits.five_hour, seven_day: .rate_limits.seven_day, updated_at: (now|floor)}' \
    > "$STATE/usage.json.tmp" && mv "$STATE/usage.json.tmp" "$STATE/usage.json"
fi

MODEL=$(printf '%s' "$input" | jq -r '.model.display_name // "Claude"')
CTX=$(printf '%s' "$input" | jq -r '.context_window.used_percentage // 0 | floor')
FIVE=$(printf '%s' "$input" | jq -r '.rate_limits.five_hour.used_percentage // empty | floor')
WEEK=$(printf '%s' "$input" | jq -r '.rate_limits.seven_day.used_percentage // empty | floor')

line="[$MODEL] ctx ${CTX}%"
[ -n "$FIVE" ] && line="$line | 5h ${FIVE}%"
[ -n "$WEEK" ] && line="$line | 7d ${WEEK}%"
[ -f "$STATE/handoff_requested" ] && line="$line | HANDOFF IN PROGRESS"
[ -f "$STATE/handoff_done" ] && line="$line | HANDED OFF"
echo "$line"
