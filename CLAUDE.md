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
- Release (signed): `./gradlew assembleRelease` -> `app/build/outputs/apk/release/app-release.apk`, package `com.nmmsoftwares.reelbox`. Needs `keystore.properties` + `keystore/reelbox-release.jks` (gitignored, never commit); without them the release APK is unsigned.

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
