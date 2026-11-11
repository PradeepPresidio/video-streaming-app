#!/usr/bin/env bash
# Keeps the attached phone's screen on (the screen sleeps after 10 min idle).
# Every KEEPAWAKE_INTERVAL seconds (default 480 = 8 min), it wakes the screen if asleep,
# then taps the middle of the status-bar strip, a spot that presses nothing in the app.
# Usage: bash scripts/keep-awake.sh start | stop | status
PROJ="$(cd "$(dirname "$0")/.." && pwd)"
STATE="$PROJ/.claude/state"
PIDF="$STATE/keepawake.pid"
INTERVAL="${KEEPAWAKE_INTERVAL:-480}"
mkdir -p "$STATE"

running() { [ -f "$PIDF" ] && kill -0 "$(cat "$PIDF")" 2>/dev/null; }

case "${1:-status}" in
  start)
    if running; then echo "keep-awake already running (pid $(cat "$PIDF"))"; exit 0; fi
    W=$(adb shell wm size 2>/dev/null | tail -1 | tr -d '\r' | sed -E 's/.*: ([0-9]+)x.*/\1/')
    case "$W" in ''|*[!0-9]*) W=1080 ;; esac
    nohup bash "$0" _loop "$((W / 2))" > "$STATE/keepawake.log" 2>&1 &
    echo $! > "$PIDF"
    echo "keep-awake started (pid $!, tap at x=$((W / 2)),y=10 every ${INTERVAL}s)"
    ;;
  _loop)
    X="$2"
    while true; do
      adb shell dumpsys power 2>/dev/null | grep -q 'mWakefulness=Awake' || adb shell input keyevent KEYCODE_WAKEUP
      adb shell input tap "$X" 10
      echo "$(date '+%F %T') tap"
      sleep "$INTERVAL"
    done
    ;;
  stop)
    if running; then kill "$(cat "$PIDF")"; echo "keep-awake stopped"; else echo "keep-awake not running"; fi
    rm -f "$PIDF"
    ;;
  status)
    if running; then echo "keep-awake running (pid $(cat "$PIDF"))"; else echo "keep-awake NOT running"; exit 1; fi
    ;;
  *) echo "usage: $0 start|stop|status"; exit 2 ;;
esac
