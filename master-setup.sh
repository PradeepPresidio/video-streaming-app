bash <<'REELBOX_MASTER_EOF'
# ReelBox + Claude Code master setup. Paste the whole block at your project root (zsh or bash).
set -euo pipefail
echo "==> Setting up Claude Code kit in: $(pwd)"
mkdir -p .claude/hooks scripts
backup() { [ -e "$1" ] && cp "$1" "$1.bak" && echo "backed up $1 -> $1.bak" || true; }

backup 'CLAUDE.md'
cat > 'CLAUDE.md' <<'__CLAUDE_KIT_EOF__'
# Project: ReelBox (offline streaming app)

An offline Android video app with Netflix/Hotstar-style features, using original branding.
`requirements.md` is the source of truth for what to build and what "done" means.
`guidelines.md` holds the full working rules. Read both before starting any task.

## Project facts
- Language / UI: Kotlin + Jetpack Compose (Material 3), single activity, MVVM
- Libraries: Media3 ExoPlayer, Room, DataStore, Coil, WorkManager (see requirements.md §2)
- applicationId: `com.example.reelbox`
- Launcher activity: `com.example.reelbox/.MainActivity`
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Min SDK 26 / Target: latest stable SDK installed
- Media: `bash tools/prepare_media.sh` generates `app/src/main/assets/media/` (gitignored), plus `catalog.json` (committed)
- If the Gradle project doesn't exist yet, create it first (requirements.md R0).

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Instrumented tests (on phone): `./gradlew connectedDebugAndroidTest`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Device
- Exactly one Android phone is attached over USB debugging and is the only entry in `adb devices`. No `-s` flag needed.
- The phone's screen turns off after 10 min idle. Before any device work, run `bash scripts/keep-awake.sh status`. If it isn't running, run `bash scripts/keep-awake.sh start` (a status-bar tap every 480 s). Never stop it; the relay owns it.
- Only one session may use the phone at a time.

## Usage limits and handoff (see guidelines.md §8)
- Hooks watch plan usage. At 95% you get a `USAGE LIMIT GUARD` message: stop feature work immediately and do the handoff (HANDOFF.md → PROGRESS.md → `bash .claude/hooks/handoff-commit.sh soft`), then stop.
- At 99% the hook commits, pushes and stops the session by itself.
- If you start with a `TAKEOVER` message (HANDOFF.md `Status: PENDING`), you are the next session: follow guidelines.md §8.3 before anything else.
- When everything in requirements.md's Definition of Done is met, run `touch .claude/state/all_done`.

## Rules
- IMPORTANT: a task is not done until it is verified on the phone: the build passes, the APK is installed, the feature is driven via adb, a screenshot shows the expected screen, logcat shows no crash or ANR, and tests pass. Report that evidence; don't just claim it works.
- Fix root causes. Never suppress errors, skip tests or weaken assertions to get green.
- Keep `PROGRESS.md` updated after each requirement (done / in progress / blocked, plus evidence paths) so work survives `/clear` and compaction.
- Save screenshots and UI dumps under `verification/` (gitignored). Read logcat filtered, never the full dump.
- When compacting, keep: the modified-file list, which requirements pass or fail, and the test commands.
- Commit after each requirement passes on-device, with a descriptive message.
__CLAUDE_KIT_EOF__

backup 'guidelines.md'
cat > 'guidelines.md' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup 'requirements.md'
cat > 'requirements.md' <<'__CLAUDE_KIT_EOF__'
# Requirements: ReelBox (offline streaming app)

> Source of truth for what to build and how each outcome is verified on the attached phone.
> ReelBox is an **offline** video app with the look and core features of Netflix / Disney+ Hotstar.
> All content ships with the app; nothing needs the internet at runtime.

## 1. Overview
A dark-themed streaming app: pick a profile, browse a home screen of rows, open a title, and watch
movies and series episodes in a full-featured player. It has Continue Watching, My List, search and
(simulated) downloads, and works fully in airplane mode on the attached phone.

## 2. Constraints
- **Language / UI:** Kotlin + Jetpack Compose (Material 3), single activity, MVVM, Navigation Compose
- **Libraries:** AndroidX Media3 (ExoPlayer + UI), Room, DataStore, Coil, Kotlin Coroutines/Flow, WorkManager (downloads). No others without asking.
- **SDK:** minSdk 26; compile/target the latest stable SDK installed on this machine
- **applicationId:** `com.example.reelbox`
- **Project:** create it from scratch if it doesn't exist: Gradle wrapper, Kotlin DSL, version catalog
- **Branding:** use an original name ("ReelBox"), logo, colors and artwork. Copy layout and features, **never** Netflix or Hotstar names, logos, fonts or assets.
- **No network at runtime:** no INTERNET permission is needed, and the app must work in airplane mode
- **APK size:** debug APK ≤ 120 MB

## 3. Content (sample media)
- **Catalog:** at least **6 movies** and **2 series with 3+ episodes each**, spread over at least 4 genres
  (e.g. Animation, Sci-Fi, Fantasy, Comedy, Kids).
- **Source:** Blender Foundation open movies (e.g. *Big Buck Bunny*, *Sintel*, *Tears of Steel*,
  *Elephants Dream*, *Spring*, *Cosmos Laundromat*). Before using a film, confirm on its official page
  that it is licensed CC BY, and record the title, author and license for the Credits screen.
  Series episodes may be consecutive segments cut from one film.
- **Fallback:** if a film can't be downloaded, generate placeholder clips with ffmpeg (`testsrc2` +
  tone + title text) so work is never blocked.
- **Preparation script:** `tools/prepare_media.sh` downloads and transcodes everything into
  `app/src/main/assets/media/`:
  - H.264/AAC MP4, 480p–720p, about 1 Mbps, 45–120 s per clip
  - a poster (2:3) and a backdrop (16:9) per title, made from video frames with the title text overlaid
  - `.srt` subtitles for at least 2 titles
  - a `catalog.json` describing everything
- **Git:** commit the script and `catalog.json`. Gitignore the generated media (it is reproducible).
- **`catalog.json` fields per title:** id, type (movie/series), title, year, genres[], maturity rating
  (`U`, `U/A 7+`, `U/A 13+`, `U/A 16+`, `A`), description, cast[], poster, backdrop, featured flag,
  and either a video path, duration, intro start/end and subtitles (movie) or episodes[] with the same
  fields (series).

## 4. Requirements and expected outcomes

Priority: **P0** = must have; build these first. **P1** = build after all P0 requirements pass.
Every acceptance check is run on the attached phone (guidelines §1.3) and saves screenshots under
`verification/`.

### R0: Project builds and launches (P0)
**Description:** The Gradle project exists, builds, installs and launches to the app's first screen.

**Acceptance criteria:**
1. `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass.
2. `adb install -r` succeeds. `adb shell am start -W -n com.example.reelbox/.MainActivity` shows `Status: ok`.
3. **Expected:** the ReelBox splash, then the profile picker (or home if R10 isn't built yet). No crash in logcat.

### R1: Offline content library (P0)
**Description:** The catalog and media from §3 are bundled and load with no network.

**Acceptance criteria:**
1. Turn on airplane mode: `adb shell cmd connectivity airplane-mode enable`. If that's unsupported,
   use `adb shell svc wifi disable && adb shell svc data disable`.
2. Clear data and launch the app.
3. **Expected:** every title in `catalog.json` appears somewhere on Home with its poster image (no broken images).
4. Play one movie and one episode. **Expected:** video and audio play.
5. Settings → Credits lists every film with its author and license.
6. Turn airplane mode off again afterwards.

### R2: Home screen (P0)
**Description:** A Netflix/Hotstar-style home screen:
- a hero banner of a featured title, with backdrop, title, genres and **Play** / **+ My List** buttons
- horizontal rows: **Continue Watching** (only when non-empty, with a progress bar on each card),
  **My List** (only when non-empty), **Trending**, **New Releases**, and one row per genre
- bottom navigation: **Home, Search, Downloads, Profile**

**Acceptance criteria:**
1. Launch the app and take a screenshot. **Expected:** the hero banner and at least 3 rows are visible.
2. Swipe one row horizontally and scroll the page vertically. **Expected:** content moves and new posters appear.
3. Tap **Play** on the hero. **Expected:** the player opens for the featured title.

### R3: Title details (P0)
**Description:** Tapping a poster opens a details page with:
- backdrop, title, year, maturity rating, duration or episode count, genres, description and cast
- **Play / Resume** (shows "Resume" plus the time left when there's progress), **My List** toggle and **Download**
- for series: an episode list with thumbnail, number, title, duration and progress bar (plus a season selector if there's more than one season)
- a **More Like This** row (same genre)

**Acceptance criteria:**
1. Open a movie from Home. **Expected:** all the fields above are visible.
2. Open a series. **Expected:** an episode list with 3+ episodes. Tapping episode 2 plays episode 2.
3. Tap a title in More Like This. **Expected:** its details page opens. Back returns to the previous page.

### R4: Video player (P0)
**Description:** A Media3 player that opens fullscreen in landscape, with:
- play/pause, a seek bar with elapsed and total time, and ±10 s buttons
- double-tap left or right to seek −10 s / +10 s
- controls that auto-hide after 3 s and show again on tap
- playback speed (0.5×, 1×, 1.25×, 1.5×, 2×)
- subtitles on/off (where an `.srt` exists)
- **Skip Intro** while inside an episode's intro window
- **Next Episode**: a 10 s countdown at the end of an episode that plays the next one (it can be cancelled)
- a lock-controls button
- picture-in-picture when Home is pressed during playback
- back leaves the player and saves the position

**Acceptance criteria:**
1. Play a movie, tap the screen and take a screenshot. **Expected:** controls and a landscape layout.
2. Tap +10 s twice. **Expected:** the elapsed time moves forward about 20 s.
3. Set the speed to 2×. **Expected:** the label shows 2×, and over 5 s of wall time playback advances about 10 s.
4. Turn subtitles on for a title that has them. **Expected:** subtitle text is visible in a screenshot.
5. Play an episode inside its intro window. **Expected:** Skip Intro is visible; tapping it jumps to the intro end.
6. Seek to the last 15 s of episode 1. **Expected:** the Next Episode countdown appears and episode 2 starts.
7. Press Home during playback. **Expected:** a PiP window is visible (screenshot) and keeps playing.
8. Rotate the device or toggle `user_rotation`. **Expected:** playback continues with no restart.

### R5: Continue Watching and resume (P0)
**Description:** The playback position is saved every 5 s and on exit. Resume uses the saved position.
Titles watched past 95% count as finished: they leave Continue Watching (a series moves to the next episode).

**Acceptance criteria:**
1. Watch a movie for about 30 s, press back and return to Home.
   **Expected:** the movie is first in Continue Watching with a partial progress bar.
2. Force-stop the app (`adb shell am force-stop com.example.reelbox`), relaunch and tap the card.
   **Expected:** playback resumes within ±5 s of where it stopped.
3. Seek to the last 3% and let it finish.
   **Expected:** the movie is no longer in Continue Watching. For a series episode, the next episode shows instead.
4. Long-press a Continue Watching card and choose **Remove**. **Expected:** it disappears.

### R6: My List (P0)
**Description:** Add or remove titles from the hero banner or the details page. The My List row and
the Profile → My List screen show them, newest first. The list survives restarts.

**Acceptance criteria:**
1. Add 2 titles. **Expected:** both appear in the My List row, the most recent first, and the button shows a check.
2. Force-stop and relaunch. **Expected:** the list is unchanged.
3. Remove one. **Expected:** it disappears from the row.

### R7: Search (P0)
**Description:** Instant search over title, genre and cast as you type, with:
- a poster grid of results
- a "no results" state
- recent searches (up to 5), which can be cleared
- a "Top searches" grid when the query is empty

**Acceptance criteria:**
1. Type part of a title (`adb shell input text`). **Expected:** the matching title appears.
2. Search a genre name. **Expected:** only titles in that genre appear.
3. Search `zzzz`. **Expected:** the no-results message appears.
4. Return to Search. **Expected:** the previous queries show under recent searches.

### R8: Robustness and performance (P0)
**Description:** The app doesn't crash or ANR, keeps state through rotation and process death, starts fast, and works offline.

**Acceptance criteria:**
1. Do a full run-through of R1–R7 in airplane mode. **Expected:** no `FATAL EXCEPTION` or `ANR in com.example.reelbox` in logcat.
2. Process death: open a details page, press Home, run `adb shell am kill com.example.reelbox`, then reopen from recents.
   **Expected:** the same details page is restored.
3. Cold start: run `adb shell am force-stop …`, then `adb shell am start -W -n com.example.reelbox/.MainActivity`.
   **Expected:** `TotalTime` < 2000 ms (median of 3 runs).
4. Back navigation never exits the app unexpectedly. Back on Home exits.

### R9: Downloads (P1)
**Description:** Simulated offline downloads. **Download** copies the title's media from the app's
bundled library into app storage (`files/downloads/`) through WorkManager. While it runs there's a
progress ring on the button and a progress notification; it can be paused, resumed or cancelled.
The Downloads tab lists downloaded titles with their size and total storage used. Each can be played
(from the downloaded copy) or deleted, which frees the space. For series, episodes can be downloaded
one at a time or all at once.

**Acceptance criteria:**
1. Tap Download on a movie. **Expected:** progress shows, then a "Downloaded" check.
2. Check: `adb shell run-as com.example.reelbox ls -l files/downloads`. **Expected:** the file exists and is non-empty.
3. In the Downloads tab, **Expected:** the title shows with its size. Tapping it plays the downloaded copy.
4. Delete it. **Expected:** it leaves the list and the file is gone (`run-as … ls`).
5. Start a series download and cancel it midway. **Expected:** no partial file remains.

### R10: Profiles (P1)
**Description:** A "Who's watching?" picker at launch.
- Up to 5 profiles, each with a name and an avatar (original, generated or vector artwork).
- Add, edit and delete profiles. One of them can be a **Kids** profile.
- Each profile has its own Continue Watching, My List and recent searches.
- A Kids profile only shows `U` and `U/A 7+` titles and uses a brighter theme accent.
- Profile → Switch Profile returns to the picker.

**Acceptance criteria:**
1. Create profiles "A" and "Kids" (Kids toggled on).
2. In A, add a title to My List and watch something. Switch to Kids.
   **Expected:** Kids' My List and Continue Watching are empty.
3. In Kids, browse Home and search for an `A`-rated title. **Expected:** it isn't shown anywhere.
4. Delete a profile. **Expected:** it's gone from the picker, and its data no longer appears.

### R11: Settings (P1)
**Description:** The Profile tab has a Settings screen with:
- Autoplay next episode (on/off; the player honors it)
- default subtitles (on/off)
- delete all downloads
- Credits (from R1)
- app version

**Acceptance criteria:**
1. Turn Autoplay off, then reach the end of an episode. **Expected:** no countdown or autoplay.
2. Turn default subtitles on, then start a title that has an `.srt`. **Expected:** subtitles show without a tap.

## 5. Out of scope
- Real streaming servers, accounts, login, payments, DRM, casting, ads, multiple audio tracks
- Any real Netflix/Hotstar content, trademarks or artwork
- Tablet, TV and foldable-specific layouts (phone portrait browsing plus landscape player only)

## 6. Definition of done
- [ ] All **P0** requirements (R0–R8) pass their on-device acceptance criteria, with screenshots in `verification/`
- [ ] All **P1** requirements (R9–R11) pass, or are explicitly listed in PROGRESS.md as not done with the reason
- [ ] `./gradlew assembleDebug`, `testDebugUnitTest` and `lintDebug` pass
- [ ] Instrumented UI tests cover: home → details → play, resume after force-stop, My List add/remove, search, and (P1) download/delete. `./gradlew connectedDebugAndroidTest` passes on the attached phone.
- [ ] A full airplane-mode run-through shows no crash or ANR in logcat
- [ ] Diff reviewed (`/code-review` or a review subagent); real gaps fixed
- [ ] `PROGRESS.md` lists every requirement with its status and evidence paths
__CLAUDE_KIT_EOF__

backup 'initial-prompt.md'
cat > 'initial-prompt.md' <<'__CLAUDE_KIT_EOF__'
Read `CLAUDE.md`, `guidelines.md` and `requirements.md` in full before doing anything else.
`requirements.md` defines what to build and the expected outcome for each requirement.
`guidelines.md` defines how to work, including how to verify on the attached phone.

Then work in this order:

1. **Preflight**
   - Run `adb devices` and confirm exactly one phone is connected and authorized.
   - Run `bash scripts/keep-awake.sh status`. If the loop isn't running (it taps the status bar every 480 s, because the screen sleeps after 10 min), start it with `bash scripts/keep-awake.sh start`.
   - Confirm `git remote -v` shows a GitHub `origin` and that `git push` works. Handoffs push there.
   - Check the toolchain: `java -version` (17+), `echo $ANDROID_HOME`, `ffmpeg -version`. If anything is missing, tell me.
   - If the Gradle project doesn't exist yet, create it (requirements.md R0). Then check the build works: `./gradlew assembleDebug`.
   - Add `verification/` to `.gitignore` and create `PROGRESS.md` with every requirement listed as "not started".

2. **Clarify.** If anything in `requirements.md` is ambiguous in a way that would change what you build, ask me using AskUserQuestion. Ask only about genuine blockers, not obvious things.

3. **Plan.** Explore the codebase, then write `PLAN.md`: the approach, the files to create or change, the order of requirements, and the exact on-device check for each one. **Stop and wait for my approval.**

4. **Implement**, one requirement at a time. For each one:
   - Write the code and tests.
   - Build, install the APK on the phone, drive the feature via adb and capture a screenshot plus filtered logcat (guidelines §1.3).
   - Compare against the acceptance criteria. Iterate until it passes; fix root causes only.
   - Update `PROGRESS.md` with the evidence paths, then commit.

5. **Final check**
   - Do a full clean-install run-through of every requirement on the phone.
   - Run all tests, including `./gradlew connectedDebugAndroidTest`.
   - Run a review of the full diff against `requirements.md` (a subagent or `/code-review`) and fix any real gaps.
   - Commit and push, then run `touch .claude/state/all_done`.

**Usage limits:** this work may span several sessions. If a `USAGE LIMIT GUARD` message appears at any point, stop immediately and follow the handoff procedure in `guidelines.md` §8.1. A new session will take over from `HANDOFF.md` after the limit resets.

When you finish, report:
- Each requirement: passed / failed, with screenshot paths and a short log excerpt.
- Test results.
- Any assumptions you made or open questions.
__CLAUDE_KIT_EOF__

backup 'takeover-prompt.md'
cat > 'takeover-prompt.md' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup 'SETUP.md'
cat > 'SETUP.md' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup '.claude/settings.json'
cat > '.claude/settings.json' <<'__CLAUDE_KIT_EOF__'
{
  "env": {
    "HANDOFF_SOFT_PCT": "95",
    "HANDOFF_HARD_PCT": "99"
  },
  "statusLine": {
    "type": "command",
    "command": "bash \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/statusline.sh",
    "refreshInterval": 60
  },
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          { "type": "command", "command": "bash \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/session-start.sh" }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "*",
        "hooks": [
          { "type": "command", "command": "bash \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/usage-guard.sh" }
        ]
      }
    ],
    "StopFailure": [
      {
        "matcher": "rate_limit",
        "hooks": [
          { "type": "command", "command": "bash \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/handoff-commit.sh limit-hit" }
        ]
      }
    ]
  },
  "permissions": {
    "allow": [
      "Bash(./gradlew *)",
      "Bash(adb *)",
      "Bash(git status*)",
      "Bash(git diff*)",
      "Bash(git log*)",
      "Bash(git add *)",
      "Bash(git commit *)",
      "Bash(git push*)",
      "Bash(bash scripts/keep-awake.sh *)",
      "Bash(bash tools/prepare_media.sh*)",
      "Bash(ffmpeg *)",
      "Bash(ffprobe *)",
      "Bash(bash .claude/hooks/handoff-commit.sh *)",
      "Bash(touch .claude/state/all_done)"
    ]
  }
}
__CLAUDE_KIT_EOF__

backup '.claude/hooks/statusline.sh'
cat > '.claude/hooks/statusline.sh' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup '.claude/hooks/usage-guard.sh'
cat > '.claude/hooks/usage-guard.sh' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup '.claude/hooks/handoff-commit.sh'
cat > '.claude/hooks/handoff-commit.sh' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup '.claude/hooks/session-start.sh'
cat > '.claude/hooks/session-start.sh' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

backup 'scripts/keep-awake.sh'
cat > 'scripts/keep-awake.sh' <<'__CLAUDE_KIT_EOF__'
#!/usr/bin/env bash
# Keeps the attached phone's screen on (the screen sleeps after 10 min idle).
# Every KEEPAWAKE_INTERVAL seconds (default 480 = 8 min), it wakes the screen if asleep,
# then taps the middle of the status-bar strip, a spot that presses nothing in the app.
# Usage: bash scripts/keep-awake.sh start | stop | status
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
STATE="$PROJ/.claude/state"
PIDF="$STATE/keepawake.pid"
INTERVAL="${KEEPAWAKE_INTERVAL:-480}"
mkdir -p "$STATE"

running() { [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; }

case "${1:-status}" in
  start)
    if running; then echo "keep-awake already running (pid $(cat "$PIDF"))"; exit 0; fi
    W=$(adb shell wm size 2>/dev/null | tail -1 | tr -d '\r' | sed -E 's/.*: ([0-9]+)x.*/\1/')
    case "$W" in ''|*[!0-9]*) W=1080 ;; esac
    nohup bash "$0" _loop "$((W / 2))" > "$STATE/keepawake.log" 2>&1 &
    echo $! > "$PIDF"
    echo "keep-awake started (pid $!, tap at x=$((W / 2)),y=10 every ${INTERVAL}s)"
    ;;
  _loop)
    X="$2"
    while true; do
      adb shell dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake' || adb shell input keyevent KEYCODE_WAKEUP
      adb shell input tap "$X" 10
      echo "$(date '+%F %T') tap"
      sleep "$INTERVAL"
    done
    ;;
  stop)
    if running; then kill "$(cat "$PIDF")"; echo "keep-awake stopped"; else echo "keep-awake not running"; fi
    rm -f "$PIDF"
    ;;
  status)
    if running; then echo "keep-awake running (pid $(cat "$PIDF"))"; else echo "keep-awake NOT running"; exit 1; fi
    ;;
  *) echo "usage: $0 start|stop|status"; exit 2 ;;
esac
__CLAUDE_KIT_EOF__

backup 'scripts/relay.sh'
cat > 'scripts/relay.sh' <<'__CLAUDE_KIT_EOF__'
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
__CLAUDE_KIT_EOF__

chmod +x .claude/hooks/*.sh scripts/*.sh
for p in '.claude/state/' 'verification/' 'app/src/main/assets/media/' '*.bak'; do
  grep -qxF "$p" .gitignore 2>/dev/null || echo "$p" >> .gitignore
done

echo
echo "==> Checking prerequisites"
ok=1
for c in git jq tmux adb ffmpeg java claude; do
  if command -v "$c" >/dev/null 2>&1; then echo "  [ok]      $c"; else echo "  [MISSING] $c"; ok=0; fi
done
if [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then echo "  [ok]      ANDROID_HOME"; else echo "  [MISSING] ANDROID_HOME (Android SDK path)"; ok=0; fi
if command -v adb >/dev/null 2>&1; then
  n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
  if [ "$n" = "1" ]; then echo "  [ok]      exactly one phone in adb devices"; else echo "  [CHECK]   adb devices shows $n ready devices (need exactly 1)"; ok=0; fi
fi

echo
echo "==> Git"
if [ ! -d .git ]; then git init -q && git branch -M main && echo "  initialised a new git repo (branch main)"; fi
if git remote get-url origin >/dev/null 2>&1; then
  echo "  [ok]      origin = $(git remote get-url origin)"
else
  echo "  [TODO]    no GitHub remote. Run:  git remote add origin <your-repo-url>"; ok=0
fi
command -v gh >/dev/null 2>&1 && { gh auth status >/dev/null 2>&1 && echo "  [ok]      gh authenticated" || echo "  [TODO]    run: gh auth login"; }

echo
[ "$ok" = 1 ] && echo "==> All checks passed." || echo "==> Fix the items marked MISSING/TODO/CHECK above, then start."
cat <<'NEXT'

=============================== START THE ENGINE ===============================
1) Commit the kit once:
     git add -A && git commit -m "Add Claude Code kit" && git push -u origin main

2a) Recommended (auto hand-off + auto-restart after usage resets):
     tmux new -s relay 'bash scripts/relay.sh'
   The relay sends initial-prompt.md to Claude for you; nothing to paste.
   Watch / approve PLAN.md:  Ctrl-b w  -> pick "claude-1".   Detach: Ctrl-b d
   Re-attach later:          tmux attach -t relay

2b) Manual (single session, no auto-restart):
     claude "$(cat initial-prompt.md)"
================================================================================
NEXT
REELBOX_MASTER_EOF