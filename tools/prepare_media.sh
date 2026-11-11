#!/usr/bin/env bash
# Builds ReelBox's bundled offline library.
#   - downloads the CC BY Blender films listed in tools/titles.json (cached in tools/.cache/)
#   - cuts and transcodes clips (H.264/AAC MP4, 480p, ~0.85 Mbps) into app/src/main/assets/media/
#   - makes a 2:3 poster and a 16:9 backdrop per title (video frame + title text), episode thumbnails
#   - cuts .srt subtitles for titles that have them
#   - writes app/src/main/assets/catalog.json (committed)
# If a film can't be downloaded, its clips are replaced by ffmpeg test-pattern placeholders.
#
# Usage: bash tools/prepare_media.sh [--force]     (--force re-encodes existing outputs)
# Needs: bash, curl, jq, ffmpeg/ffprobe, JDK 11+ (java). `source scripts/env.sh` provides java on this machine.
set -euo pipefail
(( BASH_VERSINFO[0] >= 4 )) || { echo "bash 4+ required (macOS: brew install bash)" >&2; exit 1; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools"
CACHE="$TOOLS/.cache"
ASSETS="$ROOT/app/src/main/assets"
MEDIA="$ASSETS/media"
META="$TOOLS/titles.json"
FORCE=0
[[ "${1:-}" == "--force" ]] && FORCE=1

[[ -f "$ROOT/scripts/env.sh" ]] && source "$ROOT/scripts/env.sh"
for bin in curl jq ffmpeg ffprobe java; do
  command -v "$bin" >/dev/null || { echo "missing required tool: $bin" >&2; exit 1; }
done
mkdir -p "$CACHE" "$MEDIA"

VBITRATE=750k; VMAX=1000k; ABITRATE=96k
FONT_TOOL=(java "$TOOLS/MediaTool.java")
log() { echo "[prepare_media] $*"; }

# ---------- 1. sources ----------
declare -A SRC_OK
for key in $(jq -r '.sources | keys[]' "$META"); do
  url=$(jq -r --arg k "$key" '.sources[$k]' "$META")
  out="$CACHE/$key.mp4"
  if [[ ! -s "$out" ]] || ! ffprobe -v error "$out" >/dev/null 2>&1; then
    log "downloading $key"
    if ! curl -fL --retry 3 -C - -o "$out" "$url" 2>/dev/null; then
      rm -f "$out"   # a resumed partial that failed is useless; retry from scratch once
      curl -fL --retry 3 -o "$out" "$url" 2>/dev/null || true
    fi
  fi
  if [[ -s "$out" ]] && ffprobe -v error "$out" >/dev/null 2>&1; then
    SRC_OK[$key]=1
  else
    log "WARNING: source $key unavailable, its clips will be placeholders"
    rm -f "$out"; SRC_OK[$key]=0
  fi
done
for key in $(jq -r '.subtitleSources | keys[]' "$META"); do
  out="$CACHE/$key.srt"
  [[ -s "$out" ]] || curl -fsSL --retry 3 -o "$out" "$(jq -r --arg k "$key" '.subtitleSources[$k]' "$META")" || rm -f "$out"
done

# ---------- 2. clips ----------
# make_clip <id> <title> <source> <start> <duration> <subtitleKey|"">
make_clip() {
  local id=$1 title=$2 src=$3 start=$4 dur=$5 sub=$6
  local out="$MEDIA/$id.mp4"
  if [[ $FORCE -eq 1 || ! -s "$out" ]]; then
    if [[ "${SRC_OK[$src]:-0}" == 1 ]]; then
      log "clip $id ($src @${start}s, ${dur}s)"
      ffmpeg -hide_banner -loglevel error -y -ss "$start" -i "$CACHE/$src.mp4" -t "$dur" \
        -vf "scale=-2:480,format=yuv420p" -r 24 \
        -c:v libx264 -profile:v main -preset slow -b:v $VBITRATE -maxrate $VMAX -bufsize 1500k \
        -g 48 -keyint_min 48 -sc_threshold 0 \
        -c:a aac -b:a $ABITRATE -ac 2 -ar 44100 -movflags +faststart "$out"
    else
      log "placeholder clip $id"
      "${FONT_TOOL[@]}" card 854 480 "$CACHE/card_$id.png" "$title"
      ffmpeg -hide_banner -loglevel error -y \
        -f lavfi -i "testsrc2=size=854x480:rate=24:duration=$dur" \
        -f lavfi -i "sine=frequency=440:sample_rate=44100:duration=$dur" \
        -i "$CACHE/card_$id.png" \
        -filter_complex "[0:v][2:v]overlay=0:0,format=yuv420p[v]" -map "[v]" -map 1:a \
        -c:v libx264 -profile:v main -b:v $VBITRATE -g 48 -c:a aac -b:a $ABITRATE -ac 2 \
        -movflags +faststart "$out"
    fi
  fi
  if [[ -n "$sub" ]]; then
    if [[ -s "$CACHE/$sub.srt" && "${SRC_OK[$src]:-0}" == 1 ]]; then
      "${FONT_TOOL[@]}" srtcut "$CACHE/$sub.srt" "$start" "$dur" "$MEDIA/$id.srt" >/dev/null
    else
      # Placeholder subtitles so the feature still works offline with placeholder clips.
      printf '1\n00:00:01,000 --> 00:00:06,000\n%s\n\n' "$title" > "$MEDIA/$id.srt"
    fi
  fi
}

duration_ms() {
  ffprobe -v error -show_entries format=duration -of csv=p=0 "$1" | awk '{printf "%d", $1*1000}'
}

# frame_from <clip> <secondsIntoClip> <vfilter> <overlay.png> <out.jpg>
frame_from() {
  ffmpeg -hide_banner -loglevel error -y -ss "$2" -i "$1" -i "$4" \
    -filter_complex "[0:v]$3[b];[b][1:v]overlay=0:0" -frames:v 1 -q:v 3 "$5"
}

DURS="$CACHE/durations.json"; echo '{}' > "$DURS"
record() { local tmp; tmp=$(mktemp); jq --arg k "$1" --argjson v "$2" '. + {($k): $v}' "$DURS" > "$tmp" && mv "$tmp" "$DURS"; }

n=$(jq '.titles | length' "$META")
for ((i = 0; i < n; i++)); do
  t=$(jq -c ".titles[$i]" "$META")
  id=$(jq -r .id <<<"$t"); title=$(jq -r .title <<<"$t"); type=$(jq -r .type <<<"$t")
  genres=$(jq -r '.genres | join("  ·  ")' <<<"$t")
  if [[ $type == movie ]]; then
    c=$(jq -c .clip <<<"$t")
    make_clip "$id" "$title" "$(jq -r .source <<<"$c")" "$(jq -r .start <<<"$c")" "$(jq -r .duration <<<"$c")" "$(jq -r '.subtitles // ""' <<<"$c")"
    record "$id" "$(duration_ms "$MEDIA/$id.mp4")"
    art_clip="$MEDIA/$id.mp4"; art_at=$(jq -r .posterAt <<<"$c")
  else
    ne=$(jq '.episodes | length' <<<"$t")
    for ((e = 0; e < ne; e++)); do
      ep=$(jq -c ".episodes[$e]" <<<"$t"); eid=$(jq -r .id <<<"$ep"); c=$(jq -c .clip <<<"$ep")
      make_clip "$eid" "$title E$(jq -r .number <<<"$ep")" "$(jq -r .source <<<"$c")" "$(jq -r .start <<<"$c")" "$(jq -r .duration <<<"$c")" "$(jq -r '.subtitles // ""' <<<"$c")"
      record "$eid" "$(duration_ms "$MEDIA/$eid.mp4")"
      if [[ $FORCE -eq 1 || ! -s "$MEDIA/${eid}_thumb.jpg" ]]; then
        ffmpeg -hide_banner -loglevel error -y -ss 20 -i "$MEDIA/$eid.mp4" -vf "scale=320:180" -frames:v 1 -q:v 4 "$MEDIA/${eid}_thumb.jpg"
      fi
    done
    art_clip="$MEDIA/$(jq -r '.episodes[1].id' <<<"$t").mp4"; art_at=25
  fi
  if [[ $FORCE -eq 1 || ! -s "$MEDIA/${id}_poster.jpg" || ! -s "$MEDIA/${id}_backdrop.jpg" ]]; then
    log "artwork $id"
    "${FONT_TOOL[@]}" title 400 600 "$CACHE/t_${id}_p.png" "$title" "$genres"
    "${FONT_TOOL[@]}" title 960 540 "$CACHE/t_${id}_b.png" "$title" "$genres"
    frame_from "$art_clip" "$art_at" "scale=-2:600,crop=400:600" "$CACHE/t_${id}_p.png" "$MEDIA/${id}_poster.jpg"
    frame_from "$art_clip" "$art_at" "scale=960:540" "$CACHE/t_${id}_b.png" "$MEDIA/${id}_backdrop.jpg"
  fi
done

# ---------- 3. catalog.json ----------
jq --slurpfile d "$DURS" --arg media "media" '
  def clipfields($id; $c): {
    video: "\($media)/\($id).mp4",
    durationMs: $d[0][$id],
    introStartMs: 0,
    introEndMs: (($c.introEnd // 0) * 1000),
    subtitles: (if $c.subtitles then "\($media)/\($id).srt" else null end)
  };
  {
    version: 1,
    credits: [.credits[] | {film, author, license, licenseUrl, url}],
    titles: [.titles[] | . as $t |
      {id, type, title, year, genres, maturity, description, cast, featured, trendingRank,
       poster: "\($media)/\(.id)_poster.jpg", backdrop: "\($media)/\(.id)_backdrop.jpg"}
      + (if .type == "movie" then clipfields(.id; .clip)
         else {episodes: [.episodes[] | {id, season, number, title, description,
                 thumbnail: "\($media)/\(.id)_thumb.jpg"} + clipfields(.id; .clip)]}
         end)]
  }' "$META" > "$ASSETS/catalog.json"

log "catalog: $(jq '.titles | length' "$ASSETS/catalog.json") titles -> $ASSETS/catalog.json"
log "media size: $(du -sh "$MEDIA" | cut -f1)"
