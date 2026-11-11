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
| R9 | Downloads | P1 | not started | | |
| R10 | Profiles | P1 | not started | | |
| R11 | Settings | P1 | not started | | |

## Modified files
- Gradle project scaffold (settings/build/gradle.properties/libs.versions.toml/wrapper 8.7), app module, scripts/env.sh
- R1: tools/prepare_media.sh, tools/titles.json, tools/MediaTool.java, app/src/main/assets/catalog.json (media/ gitignored),
  data/catalog/*, domain/HomeRowsBuilder.kt, ui/{home,components,navigation,profile,settings,search,downloads}/*,
  player/{PlayerViewModel,PlayerScreen}.kt, ReelBoxApp.kt, AppContainer.kt, scripts/ui.sh (adb tap-by-tag helper)
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
- SwiftKey (default IME) occasionally drops the last char of `adb shell input text` when commands are chained; read the field back before asserting.
