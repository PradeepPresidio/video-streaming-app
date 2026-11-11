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
