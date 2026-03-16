#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════
# PixelFace Health Data Debug Logger
# ═══════════════════════════════════════════════════════
#
# Captures ALL logs from the health data pipeline:
#   1. HealthDataManager   — passive listener registration + foreground data
#   2. PassiveDataService  — background delivery from Health Services
#   3. Complication services — HR, Steps, Calories serving to watch face
#   4. BootReceiver        — re-registration after reboot
#   5. History stores      — chart data writes
#   6. HealthConnect       — system-level health data flow
#
# Usage:
#   ./debug_health_data.sh           → live log stream
#   ./debug_health_data.sh --dump    → dump SharedPrefs + history files
#   ./debug_health_data.sh --save    → save to timestamped log file
#   ./debug_health_data.sh --full    → include system HealthConnect logs

set -euo pipefail

# ── Target the watch specifically (avoids "more than one device" error) ──
ADB_DEVICE="10.0.0.57:46565"
ADB="adb -s $ADB_DEVICE"

PKG="com.pixelface.watch"

# All PixelFace health tags
TAGS=(
  "HealthDataMgr"
  "PassiveDataSvc"
  "HrHistoryStore"
  "StepsHistStore"
  "CalHistStore"
  "BootReceiver"
  "PassiveRegWorker"
  "HealthSync"
)

# Build logcat filter string
build_filter() {
  local filter=""
  for tag in "${TAGS[@]}"; do
    filter="$filter $tag:D"
  done

  # Add system Health Services tags if --full
  if [[ "${1:-}" == "--full" ]]; then
    filter="$filter HealthServices:D HealthServicesImpl:D PassiveMonitoringClient:D"
    filter="$filter HealthServicesPassiveMonitoringClient:D WearableListenerService:D"
    filter="$filter GmsHealthServicesClient:D wearable:D"
  fi

  echo "$filter *:S"
}

# Dump SharedPreferences and history files from device
dump_data() {
  echo "═══════════════════════════════════════════════"
  echo " DUMPING HEALTH DATA FROM DEVICE ($ADB_DEVICE)"
  echo "═══════════════════════════════════════════════"
  echo ""

  echo "── pixelface_health_data (HealthDataManager writes here) ──"
  $ADB shell "run-as $PKG cat shared_prefs/pixelface_health_data.xml 2>/dev/null" || echo "  (not found)"
  echo ""

  echo "── pixelface_state (WatchFace reads from here, HomeScreen bridges) ──"
  $ADB shell "run-as $PKG cat shared_prefs/pixelface_state.xml 2>/dev/null" || echo "  (not found)"
  echo ""

  echo "── HR History (chart data) ──"
  local hr_data
  hr_data=$($ADB shell "run-as $PKG cat files/hr_history.json 2>/dev/null" || echo "[]")
  local hr_count
  hr_count=$(echo "$hr_data" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
  echo "  Entries: $hr_count"
  # Show last 5 entries
  echo "$hr_data" | python3 -c "
import sys,json
from datetime import datetime
d=json.load(sys.stdin)
if not d: print('  (empty)')
else:
  for e in d[-5:]:
    ts=datetime.fromtimestamp(e['ts']/1000).strftime('%Y-%m-%d %H:%M:%S')
    print(f'  {ts}  bpm={e[\"bpm\"]}')
" 2>/dev/null || echo "  $hr_data"
  echo ""

  echo "── Steps History (chart data) ──"
  local steps_data
  steps_data=$($ADB shell "run-as $PKG cat files/steps_history.json 2>/dev/null" || echo "[]")
  local steps_count
  steps_count=$(echo "$steps_data" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
  echo "  Entries: $steps_count"
  echo "$steps_data" | python3 -c "
import sys,json
from datetime import datetime
d=json.load(sys.stdin)
if not d: print('  (empty)')
else:
  for e in d[-5:]:
    ts=datetime.fromtimestamp(e['ts']/1000).strftime('%Y-%m-%d %H:%M:%S')
    print(f'  {ts}  steps={e[\"steps\"]}')
" 2>/dev/null || echo "  $steps_data"
  echo ""

  echo "── Calories History (chart data) ──"
  local cal_data
  cal_data=$($ADB shell "run-as $PKG cat files/calories_history.json 2>/dev/null" || echo "[]")
  local cal_count
  cal_count=$(echo "$cal_data" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
  echo "  Entries: $cal_count"
  echo "$cal_data" | python3 -c "
import sys,json
from datetime import datetime
d=json.load(sys.stdin)
if not d: print('  (empty)')
else:
  for e in d[-5:]:
    ts=datetime.fromtimestamp(e['ts']/1000).strftime('%Y-%m-%d %H:%M:%S')
    print(f'  {ts}  cal={e[\"cal\"]}')
" 2>/dev/null || echo "  $cal_data"
  echo ""

  echo "═══════════════════════════════════════════════"
  echo " ANALYSIS"
  echo "═══════════════════════════════════════════════"
  echo ""
  echo "Key things to check:"
  echo "  1. pixelface_health_data vs pixelface_state — values should match"
  echo "     (HomeScreen copies health→state every 15s ONLY when app is open)"
  echo "  2. last_update_ms — how stale is the data?"
  echo "  3. History files — are they growing? Charts need ≥2 entries"
  echo "  4. If HR=0 in prefs → passive listener may not be registered"
}

# Main entry
case "${1:-}" in
  --dump)
    dump_data
    ;;
  --save)
    LOGFILE="health_debug_$(date +%Y%m%d_%H%M%S).log"
    echo "Logging to $LOGFILE — press Ctrl+C to stop"
    $ADB logcat -c  # Clear old logs first
    $ADB logcat $(build_filter) | tee "$LOGFILE"
    ;;
  --full)
    echo "═══════════════════════════════════════════════════"
    echo " FULL HEALTH DEBUG (includes system HealthConnect)"
    echo " Press Ctrl+C to stop"
    echo "═══════════════════════════════════════════════════"
    $ADB logcat -c
    $ADB logcat $(build_filter "--full")
    ;;
  *)
    echo "═══════════════════════════════════════════════════"
    echo " PIXELFACE HEALTH DATA DEBUG LOGGER"
    echo " Tags: ${TAGS[*]}"
    echo " Press Ctrl+C to stop"
    echo "═══════════════════════════════════════════════════"
    $ADB logcat -c
    $ADB logcat $(build_filter)
    ;;
esac
