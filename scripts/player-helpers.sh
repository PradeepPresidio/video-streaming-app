#!/usr/bin/env bash
# Player check helpers (source from project root): laststate, lastf <field>, xy <tag>, node_text <tag>, tapid <tag>.
# Debug builds log "ReelBoxPlayer state ..." every second; read playback facts from there, not from UI dumps.
# Player helpers for R4 checks. Source from the project root.
UI=scripts/ui.sh
X=verification/ui.xml
node_text() { tr '>' '\n' < $X | grep "resource-id=\"$1\"" | head -1 | sed -E 's/.*text="([^"]*)".*/\1/'; }
controls_visible() { bash $UI dump; grep -q 'resource-id="player:elapsed"' $X; }
show_controls() { controls_visible || { adb shell input tap 1200 300; sleep 0.8; bash $UI dump; }; }
to_sec() { awk -F: '{ if (NF==3) print $1*3600+$2*60+$3; else print $1*60+$2 }' <<<"$1"; }
elapsed() { show_controls; to_sec "$(node_text player:elapsed)"; }
tapid() { bash $UI tap "$1" >/dev/null; }
# last logged state line field, e.g. lastf pos
lastf() { adb logcat -d -s ReelBoxPlayer:D | grep ' state ' | tail -1 | grep -oE "$1=[^ ]+" | cut -d= -f2; }
laststate() { adb logcat -d -s ReelBoxPlayer:D | grep ' state ' | tail -1 | sed 's/.*state //'; }
# coordinates of a node from the last (paused) dump
xy() { tr '>' '\n' < $X | grep -E "resource-id=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' | awk '{printf "%d %d", ($1+$3)/2, ($2+$4)/2}'; }
