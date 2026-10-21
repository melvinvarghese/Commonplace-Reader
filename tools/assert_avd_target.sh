#!/usr/bin/env bash
set -euo pipefail
report=build/reports/avd-target
mkdir -p "$report"
command -v avdmanager | tee "$report/avdmanager-path.txt"
readlink -f "$(command -v avdmanager)" | tee "$report/avdmanager-realpath.txt"
cat "$(dirname "$(readlink -f "$(command -v avdmanager)")")/../source.properties" | tee "$report/cmdline-tools-source.properties"
cp "$ANDROID_AVD_HOME/test.ini" "$report/test.ini"
cat "$report/test.ini"
test "$(readlink -f "$(command -v avdmanager)")" = "$(readlink -f "$COMMONPLACE_CMDLINE_TOOLS_BIN/avdmanager")"
grep -Fx 'target=android-37.0' "$report/test.ini"
echo 'AVD_TARGET_VERIFIED: android-37.0; emulator boot may proceed.'
