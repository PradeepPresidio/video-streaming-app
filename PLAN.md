# PLAN: ReelBox

Status: **awaiting approval**. R0 is already done and verified (see PROGRESS.md).

## 0. Environment facts (discovered in preflight)
- Phone: Xiaomi 22101316UP, Android 13 (API 33), 1080x2400. `cmd connectivity airplane-mode` works.
- JDK 17 and the Android SDK are installed through Homebrew but not on PATH → `source scripts/env.sh` before every Gradle run.
- Latest installed platform is **android-34**, so compileSdk/targetSdk = 34. Library versions are pinned to releases that support SDK 34
  (AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24 + Compose compiler 1.5.14, Compose BOM 2024.06.00, Media3 1.3.1, Room 2.6.1,
  Navigation 2.7.7, Lifecycle 2.8.3, WorkManager 2.9.0, DataStore 1.1.1, Coil 2.6.0). No libraries beyond requirements §2
  (plus the standard AndroidX/JUnit test artifacts). JSON is parsed with the platform `org.json`, so no serialization library is added.

## 1. Architecture
Single activity (`MainActivity`), Compose + Navigation Compose, MVVM, manual DI via `AppContainer` held by `ReelBoxApp : Application`.

```
com.example.reelbox
├── ReelBoxApp.kt / AppContainer.kt           manual DI (repos, Room, DataStore, WorkManager)
├── data/
│   ├── catalog/  CatalogModels.kt (Title, Episode, Credit), CatalogParser.kt (org.json), CatalogRepository.kt
│   ├── db/       ReelBoxDatabase, entities + DAOs: ProfileEntity, ProgressEntity, MyListEntity,
│   │             RecentSearchEntity, DownloadEntity
│   ├── prefs/    SettingsStore (DataStore: activeProfileId, autoplayNext, defaultSubtitles)
│   ├── ProgressRepository, MyListRepository, SearchRepository, ProfileRepository, DownloadRepository
│   └── download/ DownloadWorker (CoroutineWorker, foreground notification), DownloadManager facade
├── domain/       pure Kotlin logic (unit-tested): HomeRowsBuilder, SearchEngine, ProgressRules (95% finished,
│                 next-episode), MaturityFilter, IntroWindow/NextEpisode timing
├── player/       PlayerViewModel (owns ExoPlayer; survives rotation), PlayerScreen (PlayerView via AndroidView,
│                 custom Compose controls), PipController
└── ui/           theme/, navigation/ReelBoxNavHost, splash/, profiles/, home/, details/, search/, downloads/,
                  profile/ (Profile tab, My List screen, Settings, Credits), components/ (PosterCard, Row, ProgressBar)
```

Key decisions:
- **Every per-user table is keyed by `profileId` from the start** (default profile created on first launch), so R10 doesn't need a data migration.
- **Media URIs:** ExoPlayer reads `asset:///media/<file>.mp4` directly (assets stored uncompressed via `noCompress`); Coil loads `file:///android_asset/media/<file>.jpg`. Subtitles use `MediaItem.SubtitleConfiguration` (`text/x-subrip`), rendered by PlayerView's SubtitleView.
- **Player:** the player is a nav destination inside the same activity. When the player is on screen, the activity sets `requestedOrientation = SENSOR_LANDSCAPE` and hides the system bars, and restores both on exit. The manifest handles `configChanges`, and ExoPlayer lives in the `PlayerViewModel`, so rotation never restarts playback. PiP: `onUserLeaveHint` → `enterPictureInPictureMode` while the player is active, with controls hidden in PiP.
- **Progress:** saved every 5 s while playing, plus on pause/back/`ON_STOP`. Past 95% the item is marked finished; for a series, the next episode becomes the resume target.
- **Process death (R8.2):** Navigation Compose restores its back stack from saved state. ViewModels read their args from `SavedStateHandle`, and the splash/profile gate is skipped when the activity is restored from a saved state.
- **Downloads (R9):** a unique WorkManager job per item copies the asset into `files/downloads/<id>.part` in 64 KB chunks (throttled, so progress is visible for a few seconds), then renames it to `.mp4`. Pause = cancel the work but keep the `.part` file and its byte offset; resume continues from that offset; cancel/delete removes the files. A foreground notification shows progress (`dataSync` FGS type + `POST_NOTIFICATIONS` requested at runtime on API 33).

## 2. Content (`tools/prepare_media.sh`, R1)
- I'll confirm each film's CC BY license on its official Blender page before use. The licenses I expect: Big Buck Bunny (CC BY 3.0, Blender Foundation),
  Sintel (CC BY 3.0), Tears of Steel (CC BY 3.0), Elephants Dream (CC BY 2.5), Spring (CC BY 4.0), Cosmos Laundromat (CC BY 4.0).
- Sources are downloaded once into a gitignored cache (`tools/.cache/`). ffmpeg then cuts the clips, transcodes them to H.264/AAC 854x480 (or 640x360), at about 0.7 Mbps
  video + 64 kbps audio, and adds a poster (2:3, 400x600) and a backdrop (16:9, 960x540) per title from video frames, with the title drawn as text.
  `.srt` files are written by the script for at least 3 titles. **Fallback:** if a download fails, the script makes a `testsrc2` + sine-tone clip with the title text.
- **Catalog (12 clips, ≈70 MB of media, so the APK stays ≤ 120 MB):**
  - 6 movies, one ~60–75 s clip per film, across Animation, Comedy, Kids, Fantasy, Sci-Fi and Drama.
  - Series "Bunny Burrow Tales" (Kids/Animation, `U`): 3 episodes cut from Big Buck Bunny.
  - Series "Steel Protocol" (Sci-Fi, `A`): 3 episodes cut from Tears of Steel, ~45–55 s each.
  - Ratings are spread over all five values, so at least one `A` title is available for the R10 Kids test. Each episode gets an intro window (e.g. 0–10 s).
  - `catalog.json` follows requirements §3, plus a `credits` array (film, author, license, url).

## 3. Order of work and on-device check per requirement
Each step uses the full guidelines §1.3 loop: build + unit tests, then install, `logcat -c`, drive the feature via a UI dump + `input`, take screenshots into `verification/`, check `'*:E'` and the FATAL/ANR grep, update PROGRESS.md and commit.

| # | Req | Build | On-device check (screenshots in `verification/`) |
|---|-----|-------|------|
| 1 | R1 | prepare_media.sh, catalog parser + repository, a temporary "all titles" list, basic player, Settings→Credits | airplane mode on; `pm clear`; launch → `R1-home-*.png` showing every title's poster; play a movie and an episode → `R1-play-movie.png`, `R1-play-episode.png` (position advances in 2 shots; audio checked via `dumpsys media.audio_flinger`/`dumpsys audio` showing an active track); Credits → `R1-credits.png`; airplane mode off |
| 2 | R2 | Home (hero, Trending, New Releases, genre rows, CW/My List rows hidden when empty), bottom nav | `R2-home.png` (hero + ≥3 rows); `input swipe` on a row → `R2-row-swiped.png`; vertical scroll → `R2-scrolled.png`; tap hero Play → `R2-hero-play.png` (player with the featured title) |
| 3 | R3 | Details (all fields, Play/Resume, My List, Download placeholder state, episodes, More Like This) | movie details → `R3-movie.png`; series → `R3-series.png` (3+ episodes); tap ep 2 → `R3-ep2-playing.png` (title shows E2); More Like This tap → `R3-more-like.png`; back → `R3-back.png` |
| 4 | R4 | Full player: controls, ±10 s, double-tap, auto-hide 3 s, speed, subtitles, Skip Intro, Next Episode countdown, lock, PiP, rotation | for each of the 8 criteria: `R4-1-controls.png` … `R4-8-rotated.png`; for ±10 s and 2× speed, read the elapsed label from the UI dump (before/after values recorded in PROGRESS.md); PiP via `input keyevent KEYCODE_HOME` → `R4-7-pip.png`; rotation via `settings put system user_rotation 1` with accelerometer off, then check that the position keeps increasing |
| 5 | R5 | ProgressRepository, CW row with progress bars, finished rule, long-press Remove | watch 30 s, back → `R5-1-cw.png`; `am force-stop`, relaunch, tap the card → the UI dump's elapsed time is within ±5 s → `R5-2-resumed.png`; seek to the last 3% → `R5-3-finished.png` (movie gone / next episode shown); long-press → Remove → `R5-4-removed.png` |
| 6 | R6 | MyList repository, hero/details toggle, row + Profile→My List screen | add 2 → `R6-1-two.png` (newest first, check icon); force-stop + relaunch → `R6-2-persisted.png`; remove one → `R6-3-removed.png` |
| 7 | R7 | SearchEngine (title/genre/cast), grid, empty state, recent (5, clearable), Top searches | `input text` part of a title → `R7-1-title.png`; a genre → `R7-2-genre.png`; `zzzz` → `R7-3-none.png`; return → `R7-4-recent.png` |
| 8 | R8 | Process-death restore, back handling, startup checks | airplane-mode run-through of R1–R7 with the FATAL/ANR grep; open details → HOME → `am kill` → reopen from recents → `R8-2-restored.png`; 3× cold start, median `TotalTime` < 2000 ms; back-on-Home exits (check `dumpsys activity` top) |
| 9 | R10 | Profile picker, add/edit/delete (max 5, avatars as generated vector art), Kids flag + accent + rating filter, Switch Profile | create A + Kids → `R10-1-picker.png`; A: add My List + watch; switch to Kids → `R10-2-kids-empty.png`; search for the A title → `R10-3-kids-search.png` (absent); delete a profile → `R10-4-deleted.png` |
| 10 | R11 | Settings: autoplay, default subtitles, delete all downloads, Credits, version | autoplay off → at the end of an episode no countdown → `R11-1-no-autoplay.png`; default subs on → start a title with .srt → `R11-2-subs.png` |
| 11 | R9 | DownloadWorker + notification, button progress ring, pause/resume/cancel, Downloads tab, play local copy, delete, series per-episode/all | `R9-1-progress.png`, `R9-1-done.png`; `run-as … ls -l files/downloads`; `R9-3-downloads-tab.png`; play → the player's media URI logs the `file://` path (debug log tag `ReelBoxPlayer`); delete → `R9-4-deleted.png` + `ls`; series download cancelled midway → `ls` shows no `.part` |
| 12 | Final | — | `pm uninstall` + clean install, full airplane-mode run-through, `assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest`, review subagent over the full diff vs requirements, push, `touch .claude/state/all_done` |

R10 comes before R9 because it touches all the per-profile screens. Doing it while those screens are fresh is cheaper. R9 is the most self-contained.

## 4. Tests
- **Unit (JVM):** CatalogParser (fields, series/movie), HomeRowsBuilder (rows hidden when empty, genre rows), SearchEngine (title/genre/cast, case-insensitive, no results), ProgressRules (95% finished, next episode, resume time-left text), MaturityFilter (Kids), intro window / next-episode countdown timing, recent searches capped at 5, download offset/resume math.
- **Instrumented (`connectedDebugAndroidTest`, Compose UI test + UiAutomator):**
  `HomeToPlayerTest` (home → details → play, the player shows the title), `ResumeTest` (play → back saves progress; the activity is relaunched with
  ActivityScenario, the app's in-memory singletons are reset, and the details page shows "Resume" with the saved position. A true `am force-stop` would kill the instrumentation
  process, so the force-stop path is covered by the R5 adb check), `MyListTest` (add/remove), `SearchTest`, `DownloadTest` (download → file exists → delete → file gone).
  Room DAO tests use an in-memory DB.

## 5. Risks
- **APK size:** media budget ≈ 70 MB. I'll check `app-debug.apk` size after R1 and drop the bitrate if needed.
- **Xiaomi quirks:** PiP and `input` injection need the "USB debugging (Security settings)" toggle. Taps already work (keep-awake), but if PiP needs a per-app permission (MIUI "Picture-in-picture" app setting), I'll ask you.
- **Cold start < 2 s:** the catalog JSON is parsed off the main thread, the splash is kept short, and nothing heavy happens in `Application.onCreate`.
- **Big film downloads** (hundreds of MB each) take time on first run. The script prefers the smallest official renditions and caches them.

## 6. Assumptions (no blockers needed asking)
- The profile picker is shown at every cold launch (Netflix behaviour). A default profile "Me" is created on first launch.
- "Trending" and "New Releases" are derived from the catalog: Trending = a fixed `trendingRank` field in `catalog.json`, New Releases = sorted by year.
- "Top searches" = a fixed list of popular titles from the catalog.
- Download "size" = the bytes on disk of the copied file(s); "total storage used" = the sum of those.
