# PROGRESS

Environment: run `source scripts/env.sh` before Gradle (sets JAVA_HOME=Homebrew openjdk@17,
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools). Latest installed platform: android-34.

Test commands:
- `bash scripts/runthrough.sh <tag>` (full on-device P0 run-through in airplane mode, ~6 min)
- `./gradlew assembleDebug testDebugUnitTest lintDebug`
- `./gradlew connectedDebugAndroidTest`

| Req | Title | Priority | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- |
| R0 | Project builds and launches | P0 | passed | verification/R0-splash.png, verification/R0-home.png | `Status: ok`; cold start 399–455 ms; no FATAL/ANR. Home is a placeholder until R2; picker comes with R10 |
| R1 | Offline content library | P0 | passed | verification/R1-home-1.png, R1-home-scroll1..6.png, R1-seen.txt, R1-play-movie-a/b.png, R1-play-episode-a/b.png, R1-back-to-home.png, R1-credits.png, R1-credits-2.png, R1-posters-sheet.jpg | Airplane mode on + `pm clear`: all 8 catalog ids found as posters on Home (diff vs catalog empty). Sintel + Steel Protocol E1 play (frames advance; `dumpsys audio` AudioTrack state:started for app pid). Credits lists 6 films with author + license. No FATAL/ANR. APK 97.8 MB |
| R2 | Home screen | P0 | passed | verification/R2-home.png, R2-row-swiped.png, R2-scrolled.png, R2-hero-play.png, R2-tabs-home.png | Launch: hero (backdrop w/ title+genres, maturity, Play/+My List/Info) + 3 rows (Trending, New Releases, Animation) + bottom nav. Trending swipe: visible spring,sintel,bbb,tos -> bbb,tos,caminandes,ed,bbt. Vertical scroll reveals Fantasy/Adventure/Comedy rows. Hero Play -> logcat `play Spring` (featured). All 4 tabs navigate. CW/My List rows hidden when empty (HomeRowsBuilderTest); populated in R5/R6. Hero +My List button wired in R6 |
| R3 | Title details | P0 | passed | verification/R3-movie.png, R3-more-like.png, R3-back.png, R3-series.png, R3-ep2-playing.png | Sintel details: backdrop,title,2010,U/A 13+,1m 10s,genres,Play,My List,Download,description,cast,More Like This all present (UI dump tags). Series Steel Protocol: 3 episodes w/ thumb,number,title,duration; tap E2 -> logcat `play Steel Protocol · E2 Bridge Point` (sp-s1e2.mp4). More Like This -> Spring details; back -> Sintel; back -> Home. Back from player -> series details. Resume label/time-left + episode progress bars wired in R5; My List toggle in R6; Download in R9. Season selector shown only if >1 season (catalog has 1) |
| R4 | Video player | P0 | passed | verification/R4-1-controls.png, R4-autohide-pair.png, R4-2-plus20.png, R4-3-speedmenu.png, R4-3-speed2x.png, R4-3-speed-log.txt, R4-4-subtitles.png, R4-lock-doubletap-pair.png, R4-5-skipintro.png, R4-6-pair.png, R4-6-ep2-started.png, R4-6-cancelled-end.png, R4-7-pip.png, R4-7-pip-later.png, R4-7-pip-pair.png, R4-8-rotated.png, R4-8-portrait.png, R4-back-to-details.png, R4-paused-ui.xml | 1) landscape + all controls. 2) +10s x2: pos 5639->25639 (+20.0s), label 0:25. 3) 2x label; 5.1s wall -> +10.1s (ratio 1.98). 4) ToS subs on: cue text visible. 5) E1 at 0:02 Skip Intro shown; tap -> pos 10000 (intro end). 6) seek 0:40 of E1: countdown 10..1 then sp-s1e2 starts; Cancel on E2 -> ends w/o autoplay. 7) Home -> task mode=pinned, pos 8.2s->13.2s playing in PiP. 8) wm user-rotation 1->3->0->1 while playing: pos 24.3->35.4s, 0 reloads. Also: auto-hide 3s, lock blocks double-tap, double-tap +-10s, back arrow -> details portrait. No FATAL/ANR. Position save on back is implemented in R5 |
| R5 | Continue Watching and resume | P0 | passed | verification/R5-1-cw.png, R5-2-resumed.png, R5-2-details-resume.png, R5-3-before.png, R5-3-finished.png, R5-3-series-details.png, R5-3-series-next.png, R5-4-longpress.png, R5-4-removed.png, R5-3-4-triple.png, R5-episode-progress.png | 1) 30 s of Sintel + back: CW first row, partial bar (save pos=29500). 2) force-stop while playing at 57648 ms (last periodic save 57390) -> relaunch, tap CW card -> `play Sintel start=57390` (0.26 s off). Details shows Resume + '1m 3s left'. 3) ToS seek to 97% and finish -> CW row gone; Sintel also dropped after natural finish. Series: BBT E1 finished (countdown cancelled) -> details 'Play S1:E2', CW card opens `bbt-s1e2`. 4) long-press -> dialog -> Remove -> CW row gone. Episode progress bar on details confirmed. No FATAL/ANR |
| R6 | My List | P0 | passed | verification/R6-1-two.png, R6-1-details-check.png, R6-1-pair.png, R6-2-persisted.png, R6-2-profile-mylist.png, R6-3-removed.png, R6-2-3-triple.png | Clean data: add Spring via hero, Sintel via details -> My List row `sintel spring` (newest first), hero '✓ My List' (content-desc 'In My List'), details ✓. force-stop+relaunch -> row unchanged; Profile > My List grid same order. Remove Spring via hero -> row `sintel`. No FATAL/ANR |
| R7 | Search | P0 | passed | verification/R7-0-empty.png, R7-1-title.png, R7-2-genre.png, R7-3-none.png, R7-1-2-3-triple.png, R7-4-recent.png, R7-4-cleared.png, R7-4-recent-max5.png | 1) 'sint' -> sintel. 2) 'Comedy' -> big-buck-bunny, bunny-burrow-tales, caminandes-llamigos = exactly catalog Comedy titles. 3) 'zzzz' -> 'No results for “zzzz”'. 4) Home then back to Search: recent zzzz|Comedy|sint; tapping recent re-runs; Clear empties; 6 queries -> 5 kept (oldest dropped). Empty query shows Top searches grid. No FATAL/ANR |
| R8 | Robustness and performance | P0 | passed | verification/R8-airplane/report.txt (+ r*.png, crashes.txt), R8-2-triple.png, R8-3-coldstart.txt, R8-4-exited.png, connected-test.log | 1) `scripts/runthrough.sh` airplane-mode run-through of R1–R8: 30/30 PASS, crashes.txt empty (no FATAL/ANR). 2) details -> Home -> `am kill` -> Recents: same details page (new pid). 3) cold start TotalTime 374–389 ms (median 380); run-through 373/376/376. 4) back: tabs->Home, Credits->Settings->Profile->Home, player->details->details->Home, back on Home -> launcher. Instrumented: 4/4 pass (connectedDebugAndroidTest) |
| R9 | Downloads | P1 | passed | verification/R9-0-details.png, R9-1-progress.png, R9-1-done.png, R9-3-downloads-tab.png, R9-3-playing-local.png, R9-4-deleted.png, R9-5-series-progress.png, R9-5-cancelled.png, R9-6-paused.png, R9-6-episode-in-downloads.png, R9-7-kids-downloads.png, R9-8-notif-permission.png, R9-9-player-smoke.png | 1) BBB Download -> ring 'Downloading 22%' -> 'Downloaded' check (~10 s). 2) `run-as ls -l files/downloads`: big-buck-bunny.mp4 7425558 B (= asset size). 3) Downloads tab: 'Big Buck Bunny 7.4 MB', '1 downloaded · Using 7.4 MB'; tap -> log `play ... uri=file:///data/user/0/com.example.reelbox/files/downloads/big-buck-bunny.mp4`, pos 716->4764 ms. 4) Delete -> 'No downloads yet', ls empty. 5) Steel Protocol 'Download all' (3 parallel works, notifications 'Downloading Steel Protocol E1 62%') -> Pause/Cancel menu -> Cancel at ~80%: log 'removed sp-s1e1, freed 4390912 B' x3, ls empty immediately and 3 s later. Extra: episode pause keeps .part at 3145728 B (unchanged 3 s later), 'Paused 53%'; resume logs 'start sp-s1e1 from 3145728/5763073'; md5 of result == asset md5. Kids profile doesn't list the A-rated download. Settings > Delete all: 'Using 5.8 MB' -> '0 B', files+rows gone. Android 13 notification prompt shown on first Download; download continues. v1->v2 DB upgrade over an existing install: no crash. Tests: 43 unit, 7 instrumented (incl. downloadPlayAndDelete, seriesDownloadCancelledLeavesNoPartialFiles), lintDebug 0 errors. No FATAL/ANR. |
| R10 | Profiles | P1 | passed | verification/R10-0-picker-initial.png, R10-1-form-A.png, R10-1-form-Kids.png, R10-1-pair.png, R10-2-pair.png, R10-3-kids-search.png, R10-4-triple.png, R10-max5.png, R10-rename.png, R10-regression/report.txt | 1) picker: created A and Kids (Kids switch on, KIDS badge). 2) A: My List=Spring, CW=Sintel; switch to Kids -> Home rows `trending new genre:Animation` (no CW/My List), cyan accent. 3) Kids Home shows exactly the U/U-A 7+ set {big-buck-bunny, bunny-burrow-tales, caminandes-llamigos, spring}; top searches filtered; 'Steel'/'Protocol'(A)/'Sintel'/'Sci-Fi' -> no results. 4) Manage > A > Delete: picker = Me, Kids; DB rows for A (progress 1, my_list 1) -> 0 via FK cascade; Me shows no CW/My List. Also: 5-profile cap hides Add, rename works. Instrumented 5/5 (incl. kids test) |
| R11 | Settings | P1 | passed | verification/R11-1-settings-autoplay-off.png, R11-1-pair.png, R11-2-settings.png, R11-2-pair.png, R11-3-pair.png | 1) Autoplay off -> SP E1 played from 0:40 to end: 0 countdown state lines, 0 new play lines, ended=true on sp-s1e1 (no overlay in screenshots). 2) Subtitles on by default -> ToS started without tapping CC: CC highlighted at 0:02, cue 'Look Celia, we have to follow our passions;' visible, subs=true. Delete all downloads: 2.1 MB dummy file -> 'Using 0 B', files/downloads empty. Credits + version 1.0.0 present. No FATAL/ANR |

## Final check (2026-11-12)
- `./gradlew clean assembleDebug testDebugUnitTest lintDebug`: pass; 43 unit tests, 0 failures; lint 0 errors.
- `adb uninstall` + clean install, `bash scripts/runthrough.sh final`: 29/29 PASS, `RESULT: 0 failure(s)`, no FATAL/ANR (verification/final/report.txt, r*.png). Cold start median 378 ms.
- P1 checks in airplane mode on the same install (verification/final/p1-*.png):
  - Picker shown on launch. Sintel Download → notification prompt on first tap (download continues behind it) → "Downloaded"; `sintel.mp4` 7,817,253 B = asset.
  - Downloads tab "1 downloaded · Using 7.8 MB"; tap → `play Sintel uri=file:///data/user/0/com.example.reelbox/files/downloads/sintel.mp4`, position advanced.
  - Delete → empty state, dir empty.
  - Settings shows autoplay, subtitles, downloads used/delete, credits, version. Switch profile → picker.
  - No FATAL/ANR.
- `./gradlew connectedDebugAndroidTest`: 8/8 pass after the review fixes (homeToDetailsToPlay, resumeAfterRelaunch, myListAddAndRemove, searchTitleGenreAndNoResults, kidsProfileSeesOnlyKidsTitles, downloadPlayAndDelete, seriesDownloadCancelledLeavesNoPartialFiles, cancelCompletesWhenCallerIsCancelled).
- Diff review (review subagent over `git diff 8f835cf HEAD` vs requirements.md): no high-severity gaps; all R0–R11 criteria implemented. Fixed:
  1. (medium) Cancel/Delete ran in viewModelScope; leaving the screen while `remove()` waited for the worker's lock could strand `.part` files and a QUEUED/RUNNING row with no worker. `DownloadRepository.remove/cancelTitle/deleteAll` now run in `withContext(NonCancellable)`. New test `cancelCompletesWhenCallerIsCancelled` fails on the old code (files left after 10 s) and passes on the new.
  2. (low) `resumeAfterRelaunch` only checked a lower bound: now ±5 s. It relaunches the activity, not the process: an instrumented test can't force-stop its own process. The real `am force-stop` resume is checked by `scripts/runthrough.sh` (R5 "resume after force-stop (±5 s)": left at 51244, resumed 51244).
  3. (low) Removed the unused `androidx.lifecycle:lifecycle-process` dependency (not in §2).
- After the fixes: assembleDebug, 43 unit tests, lintDebug 0 errors, 8/8 instrumented, APK reinstalled.

## Modified files
- R9: domain/DownloadRules.kt (+test), data/db (DownloadEntity/DownloadDao, DB v2 AutoMigration, app/schemas/2.json), data/download/{DownloadWorker,DownloadRepository,DownloadStorage,DownloadLocks,DownloadNotifications}.kt, res/drawable/ic_stat_download.xml, AndroidManifest (FGS dataSync, POST_NOTIFICATIONS, SystemForegroundService), ui/components/DownloadButton.kt, ui/details (download state, Download all, per-episode buttons), ui/downloads/DownloadsScreen.kt (+VM), player plays file:// copy when opened from Downloads (`local` arg), Settings delete-all via repository, ReelBoxFlowsTest (+2 tests), lint fixes (Media3 @UnstableApi opt-in, unused seek increments/colours)
- Gradle project scaffold (settings/build/gradle.properties/libs.versions.toml/wrapper 8.7), app module, scripts/env.sh
- R1: tools/prepare_media.sh, tools/titles.json, tools/MediaTool.java, app/src/main/assets/catalog.json (media/ gitignored),
  data/catalog/*, domain/HomeRowsBuilder.kt, ui/{home,components,navigation,profile,settings,search,downloads}/*,
  player/{PlayerViewModel,PlayerScreen}.kt, ReelBoxApp.kt, AppContainer.kt, scripts/ui.sh (adb tap-by-tag helper)
- R11: ui/settings/SettingsScreen.kt (SettingsViewModel: autoplay, default subtitles, delete all downloads w/ usage, credits, version), data/download/DownloadStorage.kt, PlayerViewModel reads settings
- R10: domain/ProfileRules.kt (+test), data/ProfileRepository (create/update/delete/select, activeProfile, visibility), ui/profiles/{Avatar,ProfilePickerScreen,ProfileEditScreen}.kt, ui/profile/ProfileScreen (header, Switch Profile), nav start = picker, Kids accent in MainActivity, visibility filter in Home/Details/Search/MyList VMs
- R8: scripts/runthrough.sh (full P0 run-through), app/src/androidTest/.../ReelBoxFlowsTest.kt (4 UI flows), media-key handling (MainActivity.mediaKeyHandler, PlayerScreen.MediaKeys)
- R7: domain/SearchEngine.kt (+test), data/SearchRepository.kt, ui/search/{SearchViewModel,SearchScreen}.kt
- R6: data/MyListRepository.kt, ui/mylist/MyListScreen.kt, Profile > My List entry, hero/details toggles, Home My List row
- R5: data/db/{Entities,Daos,ReelBoxDatabase}.kt (+app/schemas), data/prefs/SettingsStore.kt, data/{ProfileRepository,ProgressRepository}.kt, domain/ProgressRules.kt (+test), AppContainer (appScope, DB), HomeViewModel/HomeScreen (CW row, remove dialog), DetailsViewModel (resume target, episode progress), PlayerViewModel (resume start, saves every 5 s/stop/end/exit)
- R4: player/{PlayerViewModel,PlayerScreen}.kt (custom controls, PiP, lock, speed, subs, skip intro, next-episode), domain/PlayerRules.kt (+test), MainActivity (enableEdgeToEdge, onUserLeaveHint PiP), scripts/player-helpers.sh
- R3: ui/details/{DetailsViewModel,DetailsScreen}.kt, domain/Recommendations.kt (+test), nav route details/{titleId}
- R2: ui/home/HomeScreen.kt (compact 2:1 hero), ui/components/Components.kt (88dp posters, alignment param)

## Helpers
- `bash scripts/ui.sh tap <testTag|text|content-desc regex>` taps a node from a fresh uiautomator dump
  (Compose testTags are exposed as resource-ids).
- Media: `bash tools/prepare_media.sh` (needs ~560 MB cache in tools/.cache; ~5 min).

## Assumptions
- Settings (autoplay, default subtitles) are app-wide, not per profile.
- 'Who's watching?' shows on every cold launch (not after process-death restore). Deleting the last profile recreates 'Me'.
- Kids profiles: titles above U/A 7+ are hidden everywhere (rows, hero, CW, My List, search, More Like This) and their details page reports 'not found'.
- A search counts as 'recent' when submitted, when a result is opened, or after the query is unchanged for 1.5 s (min 2 chars).
- A query equal to a genre name returns only that genre; otherwise substring match on title/genre/cast.
- Continue Watching 'Remove' deletes that title's progress for the profile (episode bars reset too).
- A default profile 'Me' is created on first launch; all progress/My List/recent searches are keyed by profile (R10).
- Films: Big Buck Bunny, Elephants Dream, Sintel, Tears of Steel, Spring, Caminandes 3: Llamigos (licences confirmed on the
  official pages, quoted in tools/titles.json). Cosmos Laundromat was dropped: no official page states its licence.
  Sources: Blender's official PeerTube (video.blender.org) 480p renditions; ToS English subtitles from download.blender.org.
- Spring: studio.blender.org says CC BY 4.0 (video.blender.org's metadata says BY-SA); the official film page is used.
- Local ffmpeg lacks drawtext, so title text is rendered by tools/MediaTool.java (JDK AWT) and overlaid with ffmpeg.
- Browsing is locked to portrait; only the player is landscape (requirements §5).
- compileSdk/targetSdk 34 = latest stable platform installed on this machine; library versions pinned to ones that support SDK 34.

- Downloads are device-wide (shared by profiles) but each profile only lists titles it may watch. "Download all" runs episodes in parallel;
  Cancel on a series removes every unfinished episode (finished ones stay); Delete on a fully downloaded series removes all episodes.
- The simulated download copies the bundled asset at ~0.5 MB/s (64 KB chunks, 120 ms apart) so progress, pause and cancel are visible.

## Learned / gotchas
- uiautomator dump takes ~8 s while video plays (UI never idle) -> controls auto-hide before the dump. Pause first
  (dump is ~2 s), or read playback facts from the debug `ReelBoxPlayer state ...` log line (1/s).
- DropdownMenu items live in a popup window: testTag resource-ids are not exposed there; find them by text.
- Two taps <300 ms apart and <275 px apart register as a double-tap seek. Show controls with a tap away from centre.
- `settings put system user_rotation` has no effect while the player requests SENSOR_LANDSCAPE; use
  `wm fixed-to-user-rotation enabled` + `wm user-rotation lock N` for rotation tests, then restore `default`/`free`.
- Without enableEdgeToEdge() the landscape window extends under the hidden nav bar but Compose insets are 0;
  controls there get empty bounds in dumps. The player pads overlays by navigationBarsIgnoringVisibility+displayCutout.
- PiP works on this Xiaomi without extra permission.
- MIUI blocks background activity starts (instrumentation's ActivityScenario) unless app-op MIUIOP(10021) is allowed;
  ReelBoxFlowsTest grants it in @BeforeClass via shell. connectedDebugAndroidTest uninstalls the app afterwards -> reinstall.
- The run-through pauses with KEYCODE_MEDIA_PAUSE (app handles media keys) so dumps are fast and the intro window isn't missed.
- First launch right after an install is slow (~1.8-2.0 s, ART compiling); steady-state cold start ~410 ms. Measure after one warm-up launch.
- CancellationException is an IllegalStateException in Kotlin: catching ISE (setForeground) must rethrow it, or pause/cancel is swallowed.
- On Android 13 a fresh test install lacks POST_NOTIFICATIONS; the permission dialog pauses the activity mid-test, so ReelBoxFlowsTest grants it in @BeforeClass.
- aapt2 (AGP 8.5) rejects adaptive icons outside mipmap-anydpi-v26 even with minSdk 26, so lint's ObsoleteSdkInt hint is not applied.
- SwiftKey (default IME) occasionally drops the last char of `adb shell input text` when commands are chained; read the field back before asserting.
