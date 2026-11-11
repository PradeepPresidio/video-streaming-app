# Setup

## 1. Copy into your project root
```
CLAUDE.md                     guidelines.md        requirements.md
initial-prompt.md             takeover-prompt.md
.claude/settings.json
.claude/hooks/statusline.sh   usage-guard.sh   handoff-commit.sh   session-start.sh
scripts/keep-awake.sh         scripts/relay.sh
```
If you already have a `.claude/settings.json`, merge in the `env`, `statusLine`, `hooks` and `permissions` blocks.

## 2. Prerequisites (macOS / Linux / WSL)
- `jq`, `tmux`, `adb`, `git` and `ffmpeg` on PATH
- JDK 17+ and the Android SDK (cmdline-tools, a recent platform and build-tools), with `ANDROID_HOME` set
- Internet on this computer, for Gradle dependencies and downloading the sample films (the app itself runs offline)
- Claude Code signed in with a **Pro or Max** claude.ai plan. Usage percentages are only reported for these plans; see the note below.
- A GitHub `origin` remote that pushes without a prompt (`gh auth login`, or an SSH key)
- `chmod +x .claude/hooks/*.sh scripts/*.sh`
- Fill in the `<...>` placeholders in `CLAUDE.md` and write your outcomes in `requirements.md`

## 3. Run
```bash
cd <project root>
tmux new -s relay 'bash scripts/relay.sh'
```
- `Ctrl-b w` lists windows; open `claude-1` to watch or answer (for example, to approve PLAN.md).
- `Ctrl-b d` detaches. Everything keeps running. Re-attach later with `tmux attach -t relay`.
- Keep the computer awake and online while the relay runs.

## 4. What happens at the limit
1. Plan usage reaches **95%**. The guard tells Claude to write `HANDOFF.md`, update `PROGRESS.md` and run `handoff-commit.sh`, which commits with a `[HANDOFF] …` message and pushes.
2. At **99%** (Claude didn't finish in time), the guard commits, pushes and stops the session itself, with a fallback HANDOFF.md.
3. The relay closes that session, sleeps until the reset time + 2 min, then starts a **new** session with `takeover-prompt.md`. The SessionStart hook injects HANDOFF.md as context.
4. This repeats until Claude runs `touch .claude/state/all_done`.

## Tuning (environment variables)
| Variable | Default | Where |
| --- | --- | --- |
| `HANDOFF_SOFT_PCT` / `HANDOFF_HARD_PCT` | 95 / 99 | `.claude/settings.json` → `env` |
| `KEEPAWAKE_INTERVAL` | 480 s | environment before starting the relay |
| `CLAUDE_FLAGS` | `--permission-mode auto` | environment before starting the relay |
| `RESUME_MARGIN` | 120 s | environment before starting the relay |
| `MAX_SESSIONS` | 20 | environment before starting the relay |

## Notes
- **Plans:** the status line only receives `rate_limits` on claude.ai Pro/Max plans. On other plans the 95/99% guard never fires. The `rate_limit` StopFailure backstop and Claude Code's built-in auto-continue still apply.
- **Sharp jumps:** usage is sampled after each tool call. One very large turn can jump past 99%; then the StopFailure backstop commits, and the relay still takes over after the reset.
- **Weekly limit:** this also works for the 7-day limit. The relay just waits longer.
- **Commits:** handoff commits use `--no-verify` so a failing pre-commit hook can't block the handoff. They may contain work in progress; HANDOFF.md says what state it's in.
- **Logs:** `.claude/state/relay.log`, `handoff.log` and `keepawake.log`. `.claude/state/` is gitignored automatically.
