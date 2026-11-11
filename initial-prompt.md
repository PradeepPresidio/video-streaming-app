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
