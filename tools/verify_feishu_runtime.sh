#!/bin/sh
# Observation collector, NOT an automated phone/Pad concurrency acceptance test.
# Exit 1: user reports primary offline; 2: input/environment error;
# 3: concurrency unverified. There is deliberately no automated PASS path.
set -eu

if [ "$#" -ne 3 ]; then
    echo "usage: $0 <adb-path> <serial> <primary-online|primary-offline>" >&2
    exit 2
fi

adb_path=$1
serial=$2
primary_state=$3

case "$primary_state" in
    primary-online|primary-offline) ;;
    *)
        echo "invalid primary state: $primary_state" >&2
        exit 2
        ;;
esac

module_log=$(
    "$adb_path" -s "$serial" shell \
        "su -c 'grep -h \"Feishu tablet identity active\" /data/adb/lspd/log/modules_*.log 2>/dev/null | tail -n 1'"
)
if ! printf '%s\n' "$module_log" | grep -q \
    'model=23043RP34G, characteristics=tablet'; then
    echo "OBSERVATION: no matching historical identity log (current hook state unknown)"
else
    echo "OBSERVATION: matching historical identity log found (not current-process proof)"
fi

activity=$(
    "$adb_path" -s "$serial" shell dumpsys activity activities
)
if ! printf '%s\n' "$activity" | grep -q 'com.ss.android.lark/.main.app.MainActivity'; then
    echo "OBSERVATION: no MainActivity record (login state unknown)"
else
    echo "OBSERVATION: MainActivity exists (not proof of an active session)"
fi

if [ "$primary_state" = "primary-offline" ]; then
    echo "USER-REPORTED FAIL: primary phone was forced offline after test-device login"
    exit 1
fi

echo "UNVERIFIED: primary online is user-reported; verify fresh messages on both devices"
exit 3
