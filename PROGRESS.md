# PROGRESS

Environment: run `source scripts/env.sh` before Gradle (sets JAVA_HOME=Homebrew openjdk@17,
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools). Latest installed platform: android-34.

Test commands:
- `./gradlew assembleDebug testDebugUnitTest lintDebug`
- `./gradlew connectedDebugAndroidTest`

| Req | Title | Priority | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- |
| R0 | Project builds and launches | P0 | passed | verification/R0-splash.png, verification/R0-home.png | `Status: ok`; cold start 399–455 ms; no FATAL/ANR. Home is a placeholder until R2; picker comes with R10 |
| R1 | Offline content library | P0 | passed | verification/R1-home-1.png, R1-home-scroll1..6.png, R1-seen.txt, R1-play-movie-a/b.png, R1-play-episode-a/b.png, R1-back-to-home.png, R1-credits.png, R1-credits-2.png, R1-posters-sheet.jpg | Airplane mode on + `pm clear`: all 8 catalog ids found as posters on Home (diff vs catalog empty). Sintel + Steel Protocol E1 play (frames advance; `dumpsys audio` AudioTrack state:started for app pid). Credits lists 6 films with author + license. No FATAL/ANR. APK 97.8 MB |
| R2 | Home screen | P0 | not started | | |
| R3 | Title details | P0 | not started | | |
| R4 | Video player | P0 | not started | | |
| R5 | Continue Watching and resume | P0 | not started | | |
| R6 | My List | P0 | not started | | |
| R7 | Search | P0 | not started | | |
| R8 | Robustness and performance | P0 | not started | | |
| R9 | Downloads | P1 | not started | | |
| R10 | Profiles | P1 | not started | | |
| R11 | Settings | P1 | not started | | |

## Modified files
- Gradle project scaffold (settings/build/gradle.properties/libs.versions.toml/wrapper 8.7), app module, scripts/env.sh
- R1: tools/prepare_media.sh, tools/titles.json, tools/MediaTool.java, app/src/main/assets/catalog.json (media/ gitignored),
  data/catalog/*, domain/HomeRowsBuilder.kt, ui/{home,components,navigation,profile,settings,search,downloads}/*,
  player/{PlayerViewModel,PlayerScreen}.kt, ReelBoxApp.kt, AppContainer.kt, scripts/ui.sh (adb tap-by-tag helper)

## Helpers
- `bash scripts/ui.sh tap <testTag|text|content-desc regex>` taps a node from a fresh uiautomator dump
  (Compose testTags are exposed as resource-ids).
- Media: `bash tools/prepare_media.sh` (needs ~560 MB cache in tools/.cache; ~5 min).

## Assumptions
- Films: Big Buck Bunny, Elephants Dream, Sintel, Tears of Steel, Spring, Caminandes 3: Llamigos (licences confirmed on the
  official pages, quoted in tools/titles.json). Cosmos Laundromat was dropped: no official page states its licence.
  Sources: Blender's official PeerTube (video.blender.org) 480p renditions; ToS English subtitles from download.blender.org.
- Spring: studio.blender.org says CC BY 4.0 (video.blender.org's metadata says BY-SA); the official film page is used.
- Local ffmpeg lacks drawtext, so title text is rendered by tools/MediaTool.java (JDK AWT) and overlaid with ffmpeg.
- Browsing is locked to portrait; only the player is landscape (requirements §5).
- compileSdk/targetSdk 34 = latest stable platform installed on this machine; library versions pinned to ones that support SDK 34.
