#!/usr/bin/env bash
# Tiny UI-automation helpers over adb + uiautomator (coordinates always come from a fresh dump).
#   bash scripts/ui.sh dump                 -> verification/ui.xml
#   bash scripts/ui.sh find <pattern>       -> prints "x y" centre of first node whose text|content-desc|resource-id matches
#   bash scripts/ui.sh tap <pattern>        -> taps that node (exit 1 if not found)
#   bash scripts/ui.sh has <pattern>        -> exit 0 if any node matches
#   bash scripts/ui.sh texts                -> all non-empty text attributes
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
XML="$ROOT/verification/ui.xml"
mkdir -p "$ROOT/verification"
dump() {
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$XML" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "uiautomator dump failed" >&2; return 1
}
find_node() {
  # split nodes onto lines, match attribute values exactly or by regex
  { tr ">" "\n" < "$XML" | grep -E "(text|content-desc|resource-id)=\"($1)\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk '{ if (NF==4) printf "%d %d\n", ($1+$3)/2, ($2+$4)/2 }'; } || true
}
case "${1:-}" in
  dump) dump ;;
  find) dump; find_node "$2" ;;
  has) dump; [[ -n "$(find_node "$2")" ]] ;;
  tap) dump; xy=$(find_node "$2"); [[ -n "$xy" ]] || { echo "not found: $2" >&2; exit 1; }; adb shell input tap $xy; echo "tapped $2 at $xy" ;;
  texts) dump; grep -oE 'text="[^"]+"' "$XML" | sed 's/text=//' ;;
  *) echo "usage: $0 dump|find|has|tap|texts [pattern]" >&2; exit 2 ;;
esac
