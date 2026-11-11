You are taking over from a previous Claude Code session that paused near the plan usage limit.
Its knowledge transfer is in `HANDOFF.md` (also injected at session start).

Follow `guidelines.md` §8.3 (takeover) before anything else:

1. Read `HANDOFF.md`, then `CLAUDE.md`, `guidelines.md`, `requirements.md`, `PROGRESS.md` and `PLAN.md`.
2. Check that the repo matches the handoff: `git log --oneline -5` (the top commit should be the `[HANDOFF]` commit) and `git status`.
3. Preflight: run `adb devices` (exactly one phone) and `bash scripts/keep-awake.sh status` (start it if it isn't running).
4. If the handoff says the build is broken or work is mid-change, fix that first. Re-verify on the phone whatever was in progress.
5. In `HANDOFF.md`, change `Status: PENDING` to `Status: TAKEN OVER <date time>`, then commit.
6. Continue from **Next steps**, following `guidelines.md` and the per-requirement loop in `initial-prompt.md` step 4.

`PLAN.md` was already approved; don't stop for approval again unless the handoff lists open questions that block you.
When the Definition of Done in `requirements.md` is fully met, commit, push and run `touch .claude/state/all_done`.
If a `USAGE LIMIT GUARD` message appears, hand off again exactly as in `guidelines.md` §8.1.
