# Test report — touch control layer

Status: **CI-green**. Every automated check below ran on GitHub Actions for this branch
(workflow `native-touch-controls.yml`, job `validate`). Nothing is claimed from a device.

## What was actually executed and passed

| Check | Where | Result |
|-------|-------|--------|
| Full patch chain applied to the pinned upstream commit (`apply_gpu_test_patches.sh` → editor → IME → stretch → Alpha 1.0 UI → native touch extras → touch layer) | CI | PASS |
| Both new patches fail closed when applied a second time | CI | PASS |
| `verify_touch_controls.py` (Java↔native contract, capability parity, dock/keyboard completeness, release paths, typing gate, strip arrangement, single mapping strategy) | CI | PASS |
| `DrnasInputHoldTest` — tap, hold, double-tap latch, long press, movement cancel, independent keys | CI (JVM) | PASS |
| `DrnasZoomSpringTest` — dead zone, intensity, stationary hold, release, frame rate, direction, resume | CI (JVM) | PASS |
| `DrnasPointerMapTest` — identity, stretch, reverse stretch, clamping, monotonicity, sub-rect, degenerate sizes, rotation, sprite placement | CI (JVM) | PASS |
| `DrnasRelativePadTest` — precision, sub-pixel accumulation, acceleration curve, edge reach, direction, rotation, bounds, speed limits, idle suppression | CI (JVM) | PASS |
| `DrnasMouseStateTest` — press/release independence, click, latch/unlock, no duplicate events, lifecycle release, middle-button gating | CI (JVM) | PASS |
| `DrnasControlLayoutTest` — persistence round trip, migration, corruption tolerance, clamping, rotation correction, safe-area insets, sanitising, presets | CI (JVM) | PASS |
| `DrnasGestureEngineTest` — tap, slop, drag, cancel, orbit, pinch, wheel remainders, two-finger tap, pan, drag safety, configuration, lifecycle release, degenerate input | CI (JVM) | PASS |
| `DrnasShortcutStripTest` — default order, reorder clamping, hide/unhide, persistence round trip, stale and corrupt preferences, escaping | CI (JVM) | PASS |
| `DrnasGpuPolicyTest` (pre-existing) | CI (JVM) | PASS |
| `javac` compile of the **patched `BlenderActivity.java` (3 715 lines) and all overlay classes** against `platforms;android-35/android.jar` | CI | PASS |
| Pre-existing `verify_alpha_1_0.py` (identity, language, stretch, Alpha 1.0 controls) | CI + local | PASS |
| Local syntax gate: tree-sitter Java parse of the patched activity and both injected fragments | local | PASS (no error nodes) |
| Local cross-check: unresolved `drnas*` identifiers and call arity in the patched activity | local | none found |

Bugs the CI compile and tests actually caught (and which were fixed):
1. `float native = ...` in a test — `native` is a Java modifier; `javac` rejected it.
2. `DrnasPointerMap` produced `NaN` for an unmeasured (1×1) window; it is now inert until both
   rectangles are known.
3. A shortcut-strip test asserted that moving the *last* button further down changes the order; it
   is a correct no-op.

## Not verified — requires a device or a native rebuild

1. Select, drag, move/rotate/scale an object with a finger.
2. Orbit/pan/zoom in **direct-touch** mode. Direct mode is still handled by upstream GHOST
   (two fingers = pan/zoom trackpad, three fingers = Shift+MMB pan); it is deliberately not
   re-routed through the overlay because that gesture is also how Blender's own panels, lists and
   file browser scroll. Orbit is available in touchpad mode, on the navigation widget and on the
   MMB dock button. **Two-finger orbit in direct mode, and the whole native middle-button path,
   require a rebuilt `libblender.so`** — see the blocker below.
3. Right-click context menus from the RMB button and the two-finger tap.
4. MMB press-and-hold drag, and MMB + LMB combinations.
5. Numeric transform entry through the Android keyboard (`G`, digits, `Enter`).
6. Editing a Blender text field with the soft keyboard, including that view keys are refused while
   it is up.
7. File browser: folder clicks, scrolling, Open/Cancel while the overlay floats above it.
8. Reaching all four corners and both edges in both input modes, with Stretch on and off.
9. Rotation, keyboard show/hide, dialogs and resume without stuck buttons (release paths are unit
   tested; the real system sequences are not).
10. Moving/resizing/hiding each group and restarting to confirm persistence; the shortcut strip
    arranger; the gesture switches.
11. Physical mouse (hover, wheel, middle button), hardware keyboard and stylus/S Pen hover.
12. Performance impact (the overlay only posts work on events; the wheel repeat is a 110 ms tick
    while a button is held). No frame-time measurement was taken.

## Native APK attempts (2026-10-21)

The touch layer needs a `libblender.so` that contains the native input extension, so an APK was
built from source in CI (`.github/workflows/native-apk-touch-test.yml`). Results so far, all real:

| Attempt | Result | Cause found |
|---------|--------|-------------|
| 1-2 | compile failed at the GHOST stage | `AKEYCODE_MOVE_HOME` was mapped twice (upstream already handled it) — **fixed**, and the verifier now rejects duplicate key cases |
| 3-6 | compile reached ~6900/6944 then exited 1 with no compiler error | the failure was in `package.sh`, not the compile: the packaging script (patched by `add_gpu_driver_options.py`) calls the driver/smooth-zoom packagers through `$DRNAS_PATCH_ROOT`, which the new workflow did not export |
| 7 | same | diagnostics fixed (PR comment + annotations); the actual message is now known |
| 8 | queued | carries the env fix and a preflight that fails in seconds if any `DRNAS_*` variable `package.sh` uses is unset |

**No APK has been produced yet.** The compile itself works (it reaches the final link), so the
remaining work is packaging configuration, not source correctness.

## Remaining blockers and repro steps

* **Native input extension is not built here.** Middle button (actions 8/9), Shift hold (10/11),
  horizontal wheel (12), `nativeDrnasCapabilities()` and the F1–F12/numpad/Home keycodes live in
  `patch_native_touch_extra.py`, which edits `GHOST_AndroidMain.cc` / `GHOST_SystemAndroid.cc`.
  Without those, the Java layer probes the capability bitmask, reports 0, and degrades: the MMB
  dock button is absent, orbit gestures fall back to scrolling, and the navigation widget's
  frame/axis keys are shown disabled with "needs the native input update" instead of doing nothing.
  Repro of the degraded state: apply only the Java patches and open the dock.
* **No APK was produced in this environment** (no NDK/Java toolchain here); the APK path is the
  repository's own native build workflow, which now also runs the new patches, the verifier and
  the JVM tests.
* **No physical device testing.** All device-dependent items above stay unverified until someone
  installs a build.
* **Portrait mode is not claimed.** Layouts are clamped on configuration changes, but Blender's own
  UI had not been validated in portrait before this change and still has not been.
