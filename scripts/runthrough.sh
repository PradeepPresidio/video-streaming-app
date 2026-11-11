#!/usr/bin/env bash
# Full on-device run-through of the P0 acceptance checks (R1–R8), in airplane mode.
#   bash scripts/runthrough.sh [tag]      (default tag: run)  -> verification/<tag>/*.png, verification/<tag>/report.txt
# Needs the debug APK installed. Clears app data first. Turns airplane mode on, and off again at the end.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source scripts/player-helpers.sh
TAG="${1:-run}"
OUT="verification/$TAG"; mkdir -p "$OUT"
REPORT="$OUT/report.txt"; : > "$REPORT"
PKG=com.example.reelbox
FAILS=0

say() { echo "$*" | tee -a "$REPORT"; }
CRASHES="$OUT/crashes.txt"; : > "$CRASHES"
# Clearing logcat would lose earlier crashes: keep any FATAL/ANR lines before every clear.
lclear() { adb logcat -d | grep -E -A15 "FATAL EXCEPTION|ANR in $PKG" >> "$CRASHES"; adb logcat -c; }
check() { # check <name> <condition-result 0/1> <detail>
  if [ "$2" = 0 ]; then say "PASS  $1  ($3)"; else say "FAIL  $1  ($3)"; FAILS=$((FAILS+1)); fi
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-1.5}"; }
launch() { adb shell am start -W -n $PKG/.MainActivity | grep -E 'Status|TotalTime'; sleep 2.5; }
ids_in_dump() { grep -oE 'resource-id="poster:[a-z0-9-]+"' $X | sed 's/resource-id="poster://; s/"//' | sort -u; }
row_ids() { tr '>' '\n' < $X | awk -v r="row:$1\"" 'index($0,r){f=1;next} f && /resource-id="row:/{f=0} f && /resource-id="poster:/{match($0,/poster:[a-z0-9-]+/); printf "%s ", substr($0,RSTART+7,RLENGTH-7)}'; }
pause_player() { # pause via media key (no slow dump while playing), then make sure controls are visible
  adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 0.8; bash $UI dump
  grep -q 'resource-id="player:playpause"' $X || { adb shell input tap 600 300; sleep 0.8; bash $UI dump; }
}
wait_find() { # wait_find <pattern>: centre "x y" of a node (popups/dialogs can take a moment to appear)
  local xy=""; for _ in 1 2 3 4; do xy=$(bash $UI find "$1"); [ -n "$xy" ] && break; sleep 0.7; done; echo "$xy"
}
seek_fraction() { # seek_fraction <permille>  (player paused, dump current)
  read -r L T R B <<< "$(tr '>' '\n' < $X | grep 'resource-id="player:seekbar"' | grep -oE 'bounds="[^"]+"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  local x0=$((L+30)) x1=$((R-30)); adb shell input tap $(( x0 + (x1-x0) * $1 / 1000 )) $(( (T+B)/2 )); sleep 0.8; bash $UI dump
}

say "ReelBox run-through '$TAG' $(date)"
adb shell dumpsys power | grep -q 'mWakefulness=Awake' || adb shell input keyevent KEYCODE_WAKEUP
adb shell cmd connectivity airplane-mode enable; sleep 2
say "airplane-mode: $(adb shell cmd connectivity airplane-mode | tr -d '\r')"
adb shell pm clear $PKG >/dev/null
lclear

# ---- R0/R1: launch offline, every catalog title on Home ----
say "launch: $(launch | tr '\n' ' ')"
shot r1-home
SEEN=""; for i in 1 2 3 4 5 6 7; do bash $UI dump; SEEN="$SEEN $(ids_in_dump)"; adb shell input swipe 540 1800 540 700 350; sleep 1; done
SEEN=$(tr ' ' '\n' <<<"$SEEN" | sort -u | grep -v '^$')
MISSING=$(jq -r '.titles[].id' app/src/main/assets/catalog.json | sort | comm -23 - <(echo "$SEEN"))
check "R1 all catalog titles on Home" "$([ -z "$MISSING" ] && echo 0 || echo 1)" "missing: ${MISSING:-none}"
for i in 1 2 3 4 5 6 7; do adb shell input swipe 540 600 540 2000 250; done; sleep 1

# ---- R2: hero + rows, row swipe, hero play ----
bash $UI dump
NROWS=$(grep -oE 'resource-id="row:[^"]+"' $X | wc -l | tr -d ' ')
check "R2 hero + >=3 rows" "$({ grep -q 'resource-id="hero"' $X && [ "$NROWS" -ge 3 ]; } && echo 0 || echo 1)" "rows=$NROWS"
BEFORE=$(row_ids trending); adb shell input swipe 900 1100 150 1100 300; sleep 1.2; bash $UI dump; AFTER=$(row_ids trending)
check "R2 row swipe reveals posters" "$([ "$BEFORE" != "$AFTER" ] && echo 0 || echo 1)" "$BEFORE -> $AFTER"
shot r2-swiped
lclear; tapid hero:play; sleep 3
HERO=$(jq -r '[.titles[] | select(.featured)][0].id' app/src/main/assets/catalog.json)
check "R2 hero Play opens featured" "$(adb logcat -d -s ReelBoxPlayer:I | grep -q ": play .*media/$HERO.mp4" && echo 0 || echo 1)" "featured=$HERO"
shot r2-hero-play; back 2

# ---- R3: details fields, series episode 2, More Like This + back ----
bash $UI dump; adb shell input tap $(rowposter trending sintel); sleep 1.5; bash $UI dump
MISSINGF=""; for f in backdrop title year maturity length genres play mylist download description cast; do grep -q "resource-id=\"details:$f\"" $X || MISSINGF="$MISSINGF $f"; done
check "R3 movie details fields" "$([ -z "$MISSINGF" ] && echo 0 || echo 1)" "missing:${MISSINGF:- none}"
shot r3-movie
adb shell input swipe 540 1700 540 900 300; sleep 1; bash $UI dump
adb shell input tap $(rowposter morelikethis spring); sleep 1.5; bash $UI dump
OK_ML=$(grep -q 'resource-id="details:spring"' $X && echo y); back; bash $UI dump
check "R3 More Like This + back" "$([ "$OK_ML" = y ] && grep -q 'resource-id="details:sintel"' $X && echo 0 || echo 1)" "spring opened, back to sintel"
back; bash $UI dump; adb shell input tap $(rowposter new steel-protocol); sleep 1.5
adb shell input swipe 540 1700 540 900 300; sleep 1; bash $UI dump
NEP=$(grep -oE 'resource-id="episode:[a-z0-9-]+"' $X | wc -l | tr -d ' '); shot r3-series
lclear; tapid episode:sp-s1e2; sleep 3
check "R3 series has 3+ episodes, E2 plays" "$([ "$NEP" -ge 3 ] && adb logcat -d -s ReelBoxPlayer:I | grep -q ': play .*sp-s1e2.mp4' && echo 0 || echo 1)" "episodes=$NEP"
back 2

# ---- R4: player (ToS movie): +20 s, 2x, subtitles; E1 skip intro, next episode; PiP; rotation ----
bash $UI dump; tapid episode:sp-s1e1; sleep 2.5
pause_player
check "R4 Skip Intro visible in intro" "$(grep -q 'resource-id="player:skipintro"' $X && echo 0 || echo 1)" "$(laststate | cut -c1-60)"
shot r4-skipintro
tapid player:skipintro; sleep 1
check "R4 Skip Intro jumps to intro end" "$([ "$(lastf pos)" -ge 9000 ] && echo 0 || echo 1)" "pos=$(lastf pos)"
bash $UI dump; P0=$(lastf pos); adb shell input tap $(xy player:ffwd); sleep 0.5; adb shell input tap $(xy player:ffwd); sleep 1.2; P1=$(lastf pos)
check "R4 +10 s twice" "$([ $((P1-P0)) -ge 19000 ] && [ $((P1-P0)) -le 21000 ] && echo 0 || echo 1)" "$P0 -> $P1"
seek_fraction 727
adb shell input tap $(xy player:playpause); sleep 6; shot r4-countdown
CD=$(adb logcat -d -s ReelBoxPlayer:D | grep -c 'countdown=[0-9]')
sleep 10
check "R4 Next Episode countdown then E2" "$([ "$CD" -gt 0 ] && [ "$(lastf id)" = sp-s1e2 ] && echo 0 || echo 1)" "countdown lines=$CD now=$(lastf id)"
back 2; back 1.5
bash $UI dump; adb shell input tap $(rowposter trending tears-of-steel); sleep 1.5; tapid details:play; sleep 3
pause_player; shot r4-controls
tapid player:speed; sleep 1; adb shell input tap $(wait_find '2x'); sleep 1; bash $UI dump
SL=$(node_text player:speedlabel); adb shell input tap $(xy player:subtitles); sleep 0.5; seek_fraction 100
adb shell input tap $(xy player:playpause); lclear; sleep 5.5
RATE=$(adb logcat -d -v time -s ReelBoxPlayer:D | grep ' state ' | awk '{split($2,t,":"); s=t[1]*3600+t[2]*60+t[3]; match($0,/pos=[0-9]+/); p=substr($0,RSTART+4,RLENGTH-4); if(!n){s0=s;p0=p;n=1} s1=s;p1=p} END{if (s1>s0) printf "%.2f", (p1-p0)/1000/(s1-s0); else print 0}')
shot r4-subtitles-2x
check "R4 speed 2x" "$([ "$SL" = 2x ] && awk -v r="$RATE" 'BEGIN{exit !(r>1.8 && r<2.2)}' && echo 0 || echo 1)" "label=$SL rate=$RATE"
check "R4 subtitles on" "$([ "$(lastf subs)" = true ] && echo 0 || echo 1)" "screenshot r4-subtitles-2x.png"
adb shell input keyevent KEYCODE_HOME; sleep 2.5; shot r4-pip
PIP=$(adb shell dumpsys activity activities | grep -c 'A=[0-9]*:com.example.reelbox.*mode=pinned'); Q0=$(lastf pos); sleep 3; Q1=$(lastf pos)
check "R4 PiP on Home keeps playing" "$([ "$PIP" -ge 1 ] && [ "$Q1" -gt "$Q0" ] && echo 0 || echo 1)" "pinned=$PIP pos $Q0->$Q1"
adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1; sleep 2
lclear; adb shell wm fixed-to-user-rotation enabled; adb shell wm user-rotation lock 3; sleep 2.5; R0=$(lastf pos)
adb shell wm user-rotation lock 1; sleep 2.5; R1=$(lastf pos)
adb shell wm fixed-to-user-rotation default; adb shell wm user-rotation free
check "R4 rotation without restart" "$([ "$R1" -gt "$R0" ] && [ "$(adb logcat -d -s ReelBoxPlayer:I | grep -c ': play ')" = 0 ] && echo 0 || echo 1)" "pos $R0->$R1"
# leave the player at a mid position (playing at 2x) and remember where it was
pause_player; MID=$(lastf pos); back 2; back 1.5

# ---- R5: Continue Watching + resume after force-stop ----
bash $UI dump; CWR=$(row_ids continue)
check "R5 CW shows last watched first" "$([ "$(awk '{print $1}' <<<"$CWR")" = tears-of-steel ] && echo 0 || echo 1)" "cw=$CWR"
shot r5-cw
adb shell am force-stop $PKG; lclear; launch >/dev/null; bash $UI dump
adb shell input tap $(rowposter continue tears-of-steel); sleep 3
START=$(adb logcat -d -s ReelBoxPlayer:I | grep ': play ' | tail -1 | grep -oE 'start=[0-9]+' | cut -d= -f2)
check "R5 resume after force-stop (±5 s)" "$([ $(( MID>START ? MID-START : START-MID )) -le 5000 ] && echo 0 || echo 1)" "left at $MID, resumed $START"
pause_player; seek_fraction 975; adb shell input tap $(xy player:playpause); sleep 4; back 2; bash $UI dump  # opened from CW: one back to Home
check "R5 finished movie leaves CW" "$(grep -q 'tears-of-steel' <<<"$(row_ids continue)" && echo 1 || echo 0)" "cw=$(row_ids continue)"
P=$(rowposter continue steel-protocol); adb shell input swipe $P $P 900; sleep 1.2; adb shell input tap $(wait_find 'Remove'); sleep 1.5; bash $UI dump
check "R5 long-press Remove" "$(grep -q 'steel-protocol' <<<"$(row_ids continue)" && echo 1 || echo 0)" "cw=$(row_ids continue)"

# ---- R6: My List ----
tapid hero:mylist; sleep 1; bash $UI dump; adb shell input tap $(rowposter trending sintel); sleep 1.5; tapid details:mylist; sleep 1; back
bash $UI dump; ML=$(row_ids mylist)
check "R6 My List newest first" "$([ "$ML" = "sintel $HERO " ] && echo 0 || echo 1)" "row=$ML"
adb shell am force-stop $PKG; launch >/dev/null; bash $UI dump; ML2=$(row_ids mylist)
check "R6 My List survives restart" "$([ "$ML2" = "$ML" ] && echo 0 || echo 1)" "row=$ML2"
tapid hero:mylist; sleep 1.2; bash $UI dump
check "R6 remove from My List" "$([ "$(row_ids mylist)" = "sintel " ] && echo 0 || echo 1)" "row=$(row_ids mylist)"
shot r6-mylist

# ---- R7: search ----
tapid tab:search; sleep 1.2; tapid search:field; sleep 0.6
adb shell input text "sint"; sleep 2.2; bash $UI dump; S1=$(ids_in_dump | tr '\n' ' ')
check "R7 title search" "$([ "$S1" = "sintel " ] && echo 0 || echo 1)" "$S1"
tapid search:clear; sleep 0.5; adb shell input text "Comedy"; sleep 2.2; bash $UI dump; S2=$(ids_in_dump | tr '\n' ' ')
EXP=$(jq -r '.titles[] | select(.genres|index("Comedy")) | .id' app/src/main/assets/catalog.json | sort | tr '\n' ' ')
check "R7 genre search" "$([ "$S2" = "$EXP" ] && echo 0 || echo 1)" "$S2"
tapid search:clear; sleep 0.5; adb shell input text "zzzz"; sleep 2.2; bash $UI dump
check "R7 no results" "$(grep -q 'search:noresults' $X && echo 0 || echo 1)" ""
back 0.8; tapid tab:home; sleep 1; tapid tab:search; sleep 1.2; tapid search:clear; sleep 1; bash $UI dump
REC=$(grep -oE 'resource-id="recent:[^"]+"' $X | sed 's/resource-id="recent://; s/"$//' | tr '\n' '|')
check "R7 recent searches" "$([ "$REC" = "zzzz|Comedy|sint|" ] && echo 0 || echo 1)" "$REC"
shot r7-recent
tapid tab:home; sleep 1

# ---- R8: process death restore, cold start, back exits ----
bash $UI dump; adb shell input tap $(rowposter trending tears-of-steel); sleep 1.5
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell am kill $PKG; sleep 1.5
adb shell input keyevent KEYCODE_APP_SWITCH; sleep 2; adb shell input tap $(wait_find 'ReelBox,Unlocked'); sleep 3; bash $UI dump
check "R8 process death restores details" "$(grep -q 'resource-id="details:tears-of-steel"' $X && echo 0 || echo 1)" ""
shot r8-restored
TIMES=$(for i in 1 2 3; do adb shell am force-stop $PKG; sleep 1.5; adb shell am start -W -n $PKG/.MainActivity | grep TotalTime | awk '{print $2}'; done | tr -d '\r' | sort -n | tr '\n' ' ')
MED=$(awk '{print $2}' <<<"$TIMES")
check "R8 cold start median < 2000 ms" "$([ "$MED" -lt 2000 ] && echo 0 || echo 1)" "runs=$TIMES median=$MED"
sleep 2; back; FOCUS=$(adb shell dumpsys window | grep -oE 'mCurrentFocus=[^}]+' | head -1)
check "R8 back on Home exits" "$(grep -q "$PKG" <<<"$FOCUS" && echo 1 || echo 0)" "$FOCUS"

# ---- crashes ----
lclear; CRASH=$(grep -E "FATAL EXCEPTION|ANR in $PKG" "$CRASHES" | head -5)
check "No FATAL EXCEPTION / ANR" "$([ -z "$CRASH" ] && echo 0 || echo 1)" "${CRASH:-none}"
adb shell cmd connectivity airplane-mode disable
say "airplane-mode: $(adb shell cmd connectivity airplane-mode | tr -d '\r')"
say "RESULT: $FAILS failure(s)"
exit $FAILS
