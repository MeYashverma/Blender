#!/usr/bin/env bash
# Compiles and runs the Android-free DRNAS input tests. No device or Android SDK needed.
#
#   ./run_input_tests.sh [output-dir]
#
# Used by .github/workflows/native-touch-controls.yml and by hand.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-build/tests-jvm}"
mkdir -p "$OUT"

CLASSES=(
  DrnasInputHold
  DrnasZoomSpring
  DrnasPointerMap
  DrnasRelativePad
  DrnasMouseState
  DrnasControlLayout
  DrnasGestureEngine
  DrnasShortcutStrip
  DrnasGpuPolicy
  DrnasSkin
  DrnasIcons
  DrnasDockGesture
  DrnasNavGesture
  DrnasRadialPaletteModel
)
TESTS=(
  DrnasInputHoldTest
  DrnasZoomSpringTest
  DrnasPointerMapTest
  DrnasRelativePadTest
  DrnasMouseStateTest
  DrnasControlLayoutTest
  DrnasGestureEngineTest
  DrnasShortcutStripTest
  DrnasGpuPolicyTest
  DrnasVisualDesignTest
  DrnasDockGestureTest
  DrnasNavGestureTest
  DrnasRadialPaletteModelTest
)

sources=()
for name in "${CLASSES[@]}"; do sources+=("$HERE/$name.java"); done
for name in "${TESTS[@]}"; do sources+=("$HERE/$name.java"); done

javac -Xlint:deprecation -d "$OUT" "${sources[@]}"

status=0
for name in "${TESTS[@]}"; do
  if [[ "$name" == "DrnasGpuPolicyTest" || "$name" == "DrnasVisualDesignTest" || "$name" == "DrnasDockGestureTest" || "$name" == "DrnasNavGestureTest" || "$name" == "DrnasRadialPaletteModelTest" ]]; then
    java -cp "$OUT" "org.blender.blender.$name"
  else
    java -cp "$OUT" "$name"
  fi || status=1
done

exit "$status"
