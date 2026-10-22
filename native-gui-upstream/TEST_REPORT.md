# Test report — touch control layer

## Current reimplementation status (2026-10-22)

The previously published control arrangement was explicitly rejected and is **superseded**. This branch now replaces the detached LMB/MMB/RMB cards and wheel-button row with one contiguous mouse dock (upper scroll rail, lower three-button zones, per-button double-tap latch), and replaces the ORBIT/PAN text row with a draggable orientation puck (drag to orbit, two fingers to pan), compact zoom/frame actions and an optional view-shortcut row.

The complete pinned-upstream patch chain, `verify_alpha_1_0.py`, `verify_touch_controls.py`, Python syntax checks and a parser pass over the generated Activity and Java sources passed locally. GitHub Java validation run **39808629007** also passed, including `DrnasDockGestureTest` (independent mouse zones, double-tap latch/unlock, cancellation and lifecycle release), `DrnasNavGestureTest` (tap/orbit/pan transitions and cancel release), and compilation of the reimplemented Activity against Android 35. The ARM64 build run **39808621406** was still in progress at the time of this update. No actual-app screenshot or physical-device touch review exists, so visual acceptance is **not passed**. The earlier published APK and CI results documented below belong only to the rejected previous arrangement and do not validate this reimplementation.

## Historical previous arrangement (rejected; not acceptance evidence)

Status: the earlier visual-redesign patch chain, source verifiers, Android-free JVM suite, patched Android Java compilation and ARM64 APK build passed in CI (validation run 39769568381; APK build run 39769560464; packaged APK inspected/published by run 39781473949). Those results are historical only. The actual-app screenshot and physical-device behavior remain **unverified**; no device result is claimed.

## Visual redesign status (2026-10-22)

* Replaced the high-saturation gradient debug-pill style with shared `DrnasSkin` Blender-dark
  surfaces, thin separators, restrained Blender-blue active feedback, and consistent spacing/radii.
* Added local stroked vector paths and `DrnasChrome` drawing primitives; the LMB/MMB/RMB dock uses
  differentiated mouse glyphs, with MMB centered/larger. Wheel controls are icon-led; horizontal
  scroll is kept in settings instead of permanently widening the dock.
* Added a compact XYZ orientation puck, icon-led orbit/pan/zoom, and a separately expandable view
  direction row. A one-time preference migration collapses the shortcut strip and zoom slider by
  default; shortcuts remain available as a scrollable strip.
* Active, idle and minimized opacity are implemented (`1.0`, `0.86`, `0.58`), with six-second idle
  reduction, and 120–180 ms feedback. Targets retain 48 dp minimum geometry separately from the
  smaller icons. Switches and SeekBars use the same accent/surface colors.
* New automated design-system tests cover icon/path coverage, contrast over three viewport
  luminances, sizing, alpha hierarchy and animation timing; `DrnasVisualDesignTest` passed in CI.
  Local tree-sitter parse of the patched activity and all 30 DRNAS Java files passed; both patch
  verifiers passed. The full activity and overlay Java sources compiled in CI against Android 35.
* **Actual-app screenshot gate: NOT PASSED.** There is no Android device/emulator in this sandbox.
  The app still needs to be installed and captured on a landscape phone; screenshots are required
  before calling the visual quality gate complete. Do not treat an SVG/mockup as that evidence.

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
| `DrnasVisualDesignTest` — required local vectors/path syntax, viewport contrast, 48 dp target separation, active/idle/minimized opacity, 120–180 ms feedback | CI (JVM) | PASS |
| `javac` compile of the **patched `BlenderActivity.java` and all overlay classes** against `platforms;android-35/android.jar` | CI | PASS |
| Pre-existing `verify_alpha_1_0.py` (identity, language, stretch, Alpha 1.0 controls) | CI + local | PASS |
| Local syntax gate: tree-sitter Java parse of the patched activity and both injected fragments | local | PASS (no error nodes) |
| Local cross-check: unresolved `drnas*` identifiers and call arity in the patched activity | local | none found |

Bugs the CI compile and tests actually caught (and which were fixed):
1. `float native = ...` in a test — `native` is a Java modifier; `javac` rejected it.
2. `DrnasPointerMap` produced `NaN` for an unmeasured (1×1) window; it is now inert until both
   rectangles are known.
3. A shortcut-strip test asserted that moving the *last* button further down changes the order; it
   is a correct no-op.
4. The new icon path cache key used an invalid multi-character Java character literal; fixed to a
   string literal before the successful compile.
5. The press animator was initially imported from `android.widget`; CI's Android 35 compile exposed
   that it belongs to `android.animation`. Corrected, then the full overlay compile passed.

## Not verified — requires a device or a native rebuild

1. Select, drag, move/rotate/scale an object with a finger.
2. Orbit/pan/zoom in **direct-touch** mode. Direct mode is still handled by upstream GHOST
   (two fingers = pan/zoom trackpad, three fingers = Shift+MMB pan); it is deliberately not
   re-routed through the overlay because that gesture is also how Blender's own panels, lists and
   file browser scroll. Orbit is available in touchpad mode, on the navigation widget and on the
   MMB dock button. **Two-finger orbit in direct mode remains intentionally unsupported** to preserve
   Blender panel/file-browser scrolling. The middle-button bridge is present in the previously
   shipped APK, but physical-device behaviour has not been exercised.
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

## APK build — SUCCEEDED (2026-10-22)

`DRNAS-Native-Blender-Touch-TEST-arm64.apk`, built from source on CI by
`.github/workflows/native-apk-touch-test.yml` (run 39769560464) and published/verified by inspect
run 39781473949 as the [`touch-test` prerelease](https://github.com/MeYashverma/Blender/releases/tag/touch-test).

| Fact | Value |
|------|-------|
| Size | 232 942 855 bytes (223 MiB) |
| SHA-256 | `94f368f2a5a407df7c1a46be4b01a194c039f5b9ddbdd848569fae9323b6dd5e` |
| Signed | yes (`apksigner verify` passes) |
| Identity | `hr.drnas.blendernativealpha10`, versionCode 100, versionName `1.0-alpha` |
| Native | `lib/arm64-v8a/libblender.so`, ELF aarch64, stripped |
| Bridge exports | `nativeOnDrnasInput`, `nativeDrnasCapabilities`, `nativeOnKey`, `nativeOnCommitText` |
| Classes in dex | Input classes plus `DrnasSkin`, `DrnasIcons`, `DrnasChrome`, `DrnasIconTextView`, `DrnasIconButton`, `DrnasOrientationGizmo` (+ pre-existing GPU/language/splash/input helpers) |

So the native input extension (middle button, Shift hold, horizontal wheel, horizontal scroll,
F1–F12/numpad/Home keycodes, capability probe) really is in the shipped `libblender.so`, and the
touch layer really is in the shipped `classes.dex`. This is a *packaged* claim, not a device claim.

### Build failures found and fixed along the way (all real)

| Attempt | Result | Cause |
|---------|--------|-------|
| 1–2 | compile failed at GHOST | `AKEYCODE_MOVE_HOME` mapped twice (upstream already handled it). The patch now skips key codes the source already maps, and the verifier rejects duplicates. |
| 3–7 | compiled to ~6900/6944, then exit 1 with no compiler error | not the compile: `package.sh` (patched by `add_gpu_driver_options.py`) calls the driver/smooth-zoom packagers via `$DRNAS_PATCH_ROOT`, which the workflow never exported. A preflight now checks every `DRNAS_*` variable `package.sh` uses before the build starts. |
| 8–9 | JVM suite red | two of my own test expectations were wrong: one still asserted the stray-finger bug, the other assumed `clampOffset` guarantees full containment instead of `MIN_VISIBLE_DP` reachability. Both corrected and re-verified by hand. |
| 10 | source validation failed | New visual sources exposed an invalid multi-character char literal and wrong `StateListAnimator` package; corrected and recompiled. |
| 11 | **success** | APK built, signed, inspected for the visual classes and published; checksum recorded above. |

### Still not verified

Everything in the list below. Installing this APK is the next step, and no result is claimed until
someone reports it from a device. Install note: same application id as Alpha 1.0, so uninstall an
existing Alpha 1.0 with a different signing key first.

## Remaining blockers and repro steps

* **Physical-device verification is still required.** The last released ARM64 APK contains the
  native input extension (actions 8/9, Shift hold 10/11, horizontal wheel 12, capability probe and
  extended keys), verified from the rebuilt library. The redesign's new Android drawing classes
  still need a successful CI compile/package, then screenshots and interaction checks on a device.
  Repro of the degraded state: apply only the Java patches and open the dock.
* **No APK was produced in this environment** (no NDK/Java toolchain here); the APK path is the
  repository's own native build workflow, which now also runs the new patches, the verifier and
  the JVM tests.
* **No physical device testing.** All device-dependent items above stay unverified until someone
  installs a build.
* **Portrait mode is not claimed.** Layouts are clamped on configuration changes, but Blender's own
  UI had not been validated in portrait before this change and still has not been.
