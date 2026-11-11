#!/usr/bin/env bash
# Commits and pushes everything with a [HANDOFF] marker, then marks the handoff done.
# Usage: handoff-commit.sh soft       (Claude ran it after writing HANDOFF.md)
#        handoff-commit.sh hard       (usage-guard at the hard threshold)
#        handoff-commit.sh limit-hit  (StopFailure hook: the usage limit was actually hit)
# In hard / limit-hit mode, writes a minimal HANDOFF.md if Claude did not write one.
set -u
MODE="${1:-soft}"
PROJ="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
cd "$PROJ" || exit 0
STATE=".claude/state"
mkdir -p "$STATE"
[ -t 0 ] || cat >/dev/null 2>&1   # drain hook input if any

fmt_time() { date -d "@$1" '+%Y-%m-%d %H:%M %Z' 2>/dev/null || date -r "$1" '+%Y-%m-%d %H:%M %Z'; }
log() { echo "[handoff $(date '+%F %T')] $*" >> "$STATE/handoff.log"; }

[ -f "$STATE/handoff_done" ] && { echo "Handoff already committed."; exit 0; }

now=$(date +%s)
# Resume time: from usage-guard if set, else the latest pending reset in usage.json, else 1 hour.
RESUME=$(cat "$STATE/resume_at" 2>/dev/null)
if [ -z "$RESUME" ] && [ -f "$STATE/usage.json" ]; then
  RESUME=$(jq -r --argjson now "$now" '[.five_hour.resets_at, .seven_day.resets_at] | map(select(. != null and . > $now)) | max // empty' "$STATE/usage.json" 2>/dev/null)
fi
[ -n "$RESUME" ] || RESUME=$((now + 3600))
echo "$RESUME" > "$STATE/resume_at"
PCT=$(jq -r '[.five_hour.used_percentage, .seven_day.used_percentage] | map(select(. != null)) | max // "?"' "$STATE/usage.json" 2>/dev/null || echo "?")

# Keep runtime state out of git.
grep -qxF '.claude/state/' .gitignore 2>/dev/null || echo '.claude/state/' >> .gitignore

# Fallback HANDOFF.md when Claude could not write one.
if ! grep -q '^Status: PENDING' HANDOFF.md 2>/dev/null; then
  {
    echo "# HANDOFF (auto-generated, mode: $MODE)"
    echo
    echo "Status: PENDING"
    echo "Paused at: $(fmt_time "$now")  |  Plan usage: ${PCT}%  |  Resume after: $(fmt_time "$RESUME")"
    echo
    echo "The previous session stopped before it could write a detailed handoff."
    echo "Reconstruct the state from PROGRESS.md, PLAN.md, the diff of this commit and the git log below."
    echo "Re-verify on the phone anything that was in progress before you trust it."
    echo
    echo "## Branch"
    echo "$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
    echo
    echo "## Uncommitted changes included in this commit"
    echo '```'
    git status --short 2>/dev/null | head -50
    echo '```'
    echo
    echo "## Recent commits"
    echo '```'
    git log --oneline -10 2>/dev/null
    echo '```'
    echo
    echo "## PROGRESS.md at handoff"
    if [ -f PROGRESS.md ]; then cat PROGRESS.md; else echo "(no PROGRESS.md)"; fi
  } > HANDOFF.md
  log "wrote fallback HANDOFF.md"
fi

git add -A
git commit -q --no-verify --allow-empty \
  -m "[HANDOFF] $MODE: paused at ${PCT}% plan usage; next session resumes after $(fmt_time "$RESUME")" \
  -m "Work-in-progress handoff commit. Read HANDOFF.md before continuing. Code may be mid-change." \
  && log "committed ($MODE, ${PCT}%)" || log "commit failed"

if git remote get-url origin >/dev/null 2>&1; then
  if git push -q -u origin HEAD 2>>"$STATE/handoff.log"; then log "pushed"; else log "push FAILED (see above); commit is local only"; fi
else
  log "no 'origin' remote; commit is local only"
fi

touch "$STATE/handoff_done"
rm -f "$STATE/handoff_requested"
echo "Handoff committed. Next session resumes after $(fmt_time "$RESUME")."
