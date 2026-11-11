#!/usr/bin/env bash
# Relay: runs Claude Code sessions back to back across usage-limit resets.
#  - Starts a session in a tmux window (initial-prompt.md, or takeover-prompt.md if HANDOFF.md is PENDING).
#  - When the session commits a handoff, closes it, sleeps until the limit resets, then starts the next one.
#  - Keeps the phone awake the whole time, including while waiting for the reset.
#  - Stops when Claude marks the project done (.claude/state/all_done) or a session exits without a handoff.
#
# Start it from the project root, inside tmux:
#   tmux new -s relay 'bash scripts/relay.sh'
# Watch Claude: Ctrl-b w (pick the claude-N window). Detach: Ctrl-b d. Re-attach: tmux attach -t relay
set -u
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
STATE="$PROJ/.claude/state"
CLAUDE_FLAGS="${CLAUDE_FLAGS:---permission-mode auto}"   # unattended runs must not stall on permission prompts
RESUME_MARGIN="${RESUME_MARGIN:-120}"                       # seconds to wait after the reset time
MAX_SESSIONS="${MAX_SESSIONS:-20}"
mkdir -p "$STATE"
cd "$PROJ" || exit 1

log() { echo "[relay $(date '+%F %T')] $*" | tee -a "$STATE/relay.log"; }
fmt_time() { date -d "@$1" '+%Y-%m-%d %H:%M %Z' 2>/dev/null || date -r "$1" '+%Y-%m-%d %H:%M %Z'; }

[ -n "${TMUX:-}" ] || { echo "Run inside tmux:  tmux new -s relay 'bash scripts/relay.sh'"; exit 1; }
for f in initial-prompt.md takeover-prompt.md; do
  [ -f "$f" ] || { echo "Missing $f in project root"; exit 1; }
done
if [ -f "$STATE/all_done" ]; then
  echo "$STATE/all_done exists (project marked done). Delete it to run again."; exit 0
fi

session=0
while [ "$session" -lt "$MAX_SESSIONS" ]; do
  session=$((session + 1))
  bash scripts/keep-awake.sh start | tee -a "$STATE/relay.log"

  if grep -q '^Status: PENDING' HANDOFF.md 2>/dev/null; then PROMPT_FILE=takeover-prompt.md; else PROMPT_FILE=initial-prompt.md; fi
  rm -f "$STATE/handoff_requested" "$STATE/handoff_done" "$STATE/resume_at"

  win="claude-$session"
  log "starting session $session ($PROMPT_FILE) in tmux window $win"
  tmux new-window -d -n "$win" -c "$PROJ" "claude $CLAUDE_FLAGS \"\$(cat '$PROJ/$PROMPT_FILE')\"; echo; echo '[session ended, closing in 10s]'; sleep 10"

  # Wait for: handoff committed, project done, or the window closing.
  while tmux list-windows -F '#W' | grep -qx "$win"; do
    if [ -f "$STATE/handoff_done" ]; then
      log "handoff committed; closing $win in 30s"
      sleep 30
      tmux kill-window -t "$win" 2>/dev/null
      break
    fi
    [ -f "$STATE/all_done" ] && break
    sleep 15
  done

  if [ -f "$STATE/all_done" ]; then
    log "project marked done. Leaving $win open for review. Relay exiting."
    bash scripts/keep-awake.sh stop | tee -a "$STATE/relay.log"
    exit 0
  fi

  if [ -f "$STATE/handoff_done" ]; then
    resume=$(cat "$STATE/resume_at" 2>/dev/null || echo 0)
    target=$((resume + RESUME_MARGIN))
    [ "$target" -gt "$(date +%s)" ] || target=$(date +%s)
    log "waiting for usage reset; next session at $(fmt_time "$target")"
    # Poll the wall clock (plain 'sleep N' doesn't count time the computer spends asleep).
    while [ "$(date +%s)" -lt "$target" ]; do sleep 60; done
    continue
  fi

  log "session $session ended without a handoff (exited by you, or an error). Relay stopping."
  log "Check the window output / $STATE/relay.log, then restart: tmux new -s relay 'bash scripts/relay.sh'"
  exit 0
done
log "reached MAX_SESSIONS=$MAX_SESSIONS; stopping."
