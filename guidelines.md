# Working Guidelines for Claude Code

The rules for working on this project. Most of them protect one limited resource: the
context window. It fills fast, and quality drops as it fills.

---

## 1. Verify everything on the phone

Claude stops when work *looks* done. Here, "done" means verified on the attached phone.

### 1.1 Device preflight (once per session)
```bash
adb devices                      # expect exactly ONE line ending in "device"
adb shell wm size                # e.g. "Physical size: 1080x2400"
```
- If the list is empty, or the device shows `unauthorized` or `offline`, stop and tell the user. Do not guess.

### 1.2 Keep the screen awake (mandatory, all session)
The screen turns off after **10 minutes** idle. `scripts/keep-awake.sh` runs a background loop.
Every **8 minutes (480 s)** it wakes the screen if needed and taps the middle of the status-bar strip.
That spot is neutral, so the tap never presses anything in the app. The relay (§8) starts it and keeps
it running between sessions too.

```bash
bash scripts/keep-awake.sh status    # check
bash scripts/keep-awake.sh start     # start if not running (safe to run twice)
```
- Never stop the loop yourself. The relay stops it when the project is done.

Before each verification run, confirm the screen is on:
```bash
W=$(adb shell wm size | tail -1 | tr -d '\r' | sed -E 's/.*: ([0-9]+)x.*/\1/')
adb shell dumpsys power | grep -q 'mWakefulness=Awake' || {
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell input swipe $((W/2)) 1800 $((W/2)) 400   # dismiss a non-secure lock screen
}
```
- If a PIN, pattern or password lock appears, stop and ask the user to unlock. Never try to bypass it.

### 1.3 Verification loop (after every meaningful change)
1. **Build:** `./gradlew assembleDebug` must succeed.
2. **Unit tests:** `./gradlew testDebugUnitTest`.
3. **Install:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. **Reset logs:** `adb logcat -c`.
5. **Launch:** `adb shell am start -n <applicationId>/.MainActivity`.
   For a clean state, run `adb shell pm clear <applicationId>` first.
6. **Drive the feature:** use `adb shell input tap X Y`, `input swipe`, `input text "..."` and `input keyevent`.
   - Get coordinates from a UI dump, never by guessing:
     `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml verification/ui.xml`
   - After each action, wait about 1–2 s for the UI to settle.
7. **Capture evidence:**
   - Screenshot: `adb exec-out screencap -p > verification/<req-id>-<step>.png`. Open it and look at it.
   - Logs, filtered to keep context small:
     `adb logcat -d -v brief '*:E' | tail -100`
     `adb logcat -d | grep -E 'FATAL EXCEPTION|ANR in' | head -40`
8. **Compare** with the acceptance criteria in `requirements.md`. If anything differs, fix the root cause and go back to step 1.
9. **Instrumented tests:** where they exist, run `./gradlew connectedDebugAndroidTest`.

A requirement **passes** only when the screenshot matches the expected outcome, logcat has no crash
or ANR from the app, and all tests pass. Record the evidence paths in `PROGRESS.md`.

### 1.4 Evidence, not claims
Report the commands you ran, their results, the screenshot paths and log excerpts. "It should work" is not done.

---

## 2. Explore → plan → implement → commit
1. **Explore:** read the relevant code and `requirements.md` before changing anything.
2. **Plan:** write `PLAN.md`: files to change, approach, risks, and the on-device check for each requirement.
3. **Implement** one requirement at a time, running the verification loop for each.
4. **Commit** after each requirement passes on-device.

Skip formal planning only for trivial changes (a typo, a log line, a rename).

## 3. Work precisely
- Follow the patterns already in the codebase. Find a similar screen or class and mirror it.
- Don't add libraries beyond those already used unless `requirements.md` allows it.
- When fixing a bug, first write a failing test that reproduces it, then fix it.
- Address root causes. Never suppress warnings or errors, skip tests or loosen assertions to get green.
- If a requirement is ambiguous in a way that changes what you build, ask before building. Otherwise pick the most reasonable reading and note it in `PROGRESS.md`.

## 4. Manage context
- Use subagents for broad investigation (e.g. "how is navigation wired?") so file reading stays out of the main context.
- Keep command output small: use `tail`, `grep` and filtered logcat, never the full logcat or full Gradle logs.
- Keep `PROGRESS.md` current so work survives `/clear` and compaction:
  - requirement → status (passed / in progress / blocked) → evidence paths → notes.
- If the same fix fails twice, stop, write down what you learned in `PROGRESS.md` and try a different approach rather than repeating it.

## 5. Review before calling it done
- When all requirements pass, run `/code-review`, or use a subagent to review the full diff against
  `requirements.md` and `PLAN.md`. Flag only correctness gaps and missed requirements, not style preferences.
- Fix real gaps, re-run the verification loop for the requirements involved, then update `PROGRESS.md`.

When the whole Definition of Done in `requirements.md` is met, run `touch .claude/state/all_done`.
This tells the relay to stop.

## 6. Git hygiene
- One commit per passing requirement, with a descriptive message.
- Never commit `verification/`, build outputs or secrets. Make sure `.gitignore` covers them.
- Checkpoints (`/rewind`) don't capture changes made through Bash, so git is the real safety net.

## 7. Failure patterns to avoid
| Pattern | Fix |
| --- | --- |
| Declaring done without testing on the device | Always run the verification loop (§1.3) |
| Screen asleep mid-test | Check that the keep-awake loop is alive and the screen is awake (§1.2) |
| Guessing tap coordinates | Use a UI dump |
| Dumping huge logs into context | Filter with `'*:E'`, `grep` and `tail` |
| Repeating a failing approach | Two strikes, then rethink and note it in `PROGRESS.md` |
| Scope creep beyond requirements | Build only what `requirements.md` asks for |
| Ignoring the usage guard | Stop at once and hand off (§8) |
| Vague handoff ("continue the work") | Name exact files, functions, commands and the next step (§8.2) |

---

## 8. Usage limits and session handoff

Plan usage (the 5-hour and 7-day limits) is monitored automatically:

| Piece | What it does |
| --- | --- |
| `.claude/hooks/statusline.sh` | Shows usage in the status line and records it to `.claude/state/usage.json` |
| `.claude/hooks/usage-guard.sh` (PostToolUse) | **≥ 95%:** tells you to hand off. **≥ 99%:** commits, pushes and stops the session itself |
| `.claude/hooks/handoff-commit.sh` | `git add -A`, then a commit with a `[HANDOFF]` marker, then `git push`, then flags the handoff as done |
| StopFailure hook (`rate_limit`) | Backstop: if the limit is actually hit, it auto-commits with a fallback HANDOFF.md |
| `.claude/hooks/session-start.sh` | Injects a pending HANDOFF.md into the next session |
| `scripts/relay.sh` | Closes the finished session, waits for the reset, starts the next session |

### 8.1 When you see `USAGE LIMIT GUARD`
1. **Stop feature work immediately.** Finish or revert only the edit in progress, so files aren't left half-written. Start nothing new: no builds, no device runs.
2. **Write `HANDOFF.md`** using the template in §8.2, with `Status: PENDING`.
3. **Update `PROGRESS.md`.**
4. **Run `bash .claude/hooks/handoff-commit.sh soft`.** It commits everything (WIP included) with a `[HANDOFF]` message and pushes to GitHub.
5. Reply `Handoff complete` and stop. Run no further tools; the hook will block them anyway.

### 8.2 HANDOFF.md template
Write it for a reader with **zero context**. Be concrete: give file paths, function names, commands and IDs.

```markdown
# HANDOFF

Status: PENDING
Paused at: <date time>  |  Plan usage: <pct>%  |  Resume after: <reset time>
Branch: <branch>  |  Last good commit (builds + verified): <sha>

## Where things stand
- Current requirement: <R#> - <title>, at step <n> of PLAN.md
- Requirements passed on device: <R1, R2...> (evidence in verification/)
- Requirements not started: <...>

## Work in progress (this commit may be mid-change)
- <file>: <what was changed and what's still missing>
- Builds right now: <yes / no: error summary>

## Next steps (in order)
1. <exact next action, e.g. "finish NoteRepository.delete(), then run testDebugUnitTest">
2. <...>
3. Re-run the on-device check for <R#> (guidelines §1.3)

## Learned / don't repeat
- <approaches that failed and why>
- <gotchas: device quirks, flaky tests, API surprises>

## Open questions for the user
- <...>
```

### 8.3 Takeover (when your session starts with a `TAKEOVER` message)
1. Read `HANDOFF.md`, then `CLAUDE.md`, `requirements.md`, `PROGRESS.md` and `PLAN.md`.
2. Check that the repo matches the handoff: `git log --oneline -5` (top commit should be the `[HANDOFF]` one) and `git status`.
3. Preflight: run `adb devices` and `bash scripts/keep-awake.sh status` (start it if it isn't running).
4. If the handoff says the build is broken or work is mid-change, fix that first. Then re-verify on the device whatever was in progress; don't trust unverified claims.
5. Change `Status: PENDING` to `Status: TAKEN OVER <date time>` in HANDOFF.md and commit it.
6. Continue from **Next steps**. The same handoff rules apply to you when the guard fires again.

Source: [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)
