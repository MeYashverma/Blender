# Test report — touch control layer

Written from what was actually executed in the working environment, not from expectations.

## Environment limits (read this first)

The environment used to prepare this change has **no Android SDK/NDK, no Gradle and no Java
compiler** (only a JRE could be obtained). Therefore:

* **No APK was produced here.** The native library cannot be rebuilt without the NDK and the
  upstream `lib/android_arm64` LFS payload, which is not reachable from this environment. The
  repository's own release path does not compile native code either: it repacks a previously
  accepted signed v7 APK, which also requires a GitHub Actions run (`gh run download`).
* **The Java unit tests were not executed here.** They are committed, wired into CI
  (`run_input_tests.sh` via `native-touch-controls.yml`), and their expectations were verified by
  modelling the algorithms separately (see below), but "the tests pass" is not claimed from this
  environment. The CI workflow that runs them is part of this change.
* **No physical Android device was used.** Every device-specific behaviour listed at the end is
  still unverified on hardware.

## What was executed here (and passed)

| Check | Result |
|-------|--------|
| Full patch chain on a clean checkout of the pinned upstream commit (`apply_gpu_test_patches.sh` → editor → IME v9 → stretch v10 → Alpha 1.0 UI → `patch_native_touch_extra.py` → `patch_drnas_touch_layer.py`) | PASS — each step's own assertions passed |
| Chain idempotency: applying either new patch a second time | PASS — both fail closed with a named anchor instead of corrupting sources |
| `verify_touch_controls.py` on the patched tree | PASS |
| Pre-existing `verify_alpha_1_0.py` (Alpha 1.0 identity, language, stretch, controls) | PASS |
| Java syntax of every new `.java` and `.inc` (tree-sitter Java parser) | PASS — no error nodes |
| Java syntax of the complete patched `BlenderActivity.java` (3 380 lines) | PASS — no error nodes |
| Undefined identifiers: every `drnas*`/`Drnas*` symbol used in the activity is declared | PASS |
| Call arity for every `drnas*`/`Drnas*` method call | PASS |
| Duplicate field declarations | none |
| Duplicate lifecycle methods introduced by the patch | none — `onWindowFocusChanged` already existed upstream, so the patch extends it instead of declaring a second copy |
| Pointer-map arithmetic (identity, stretch, reverse stretch, sub-rect, rotation, clamping, round-trip) | re-computed independently; matches the committed assertions |
| Layout clamping arithmetic (`clampOffset`, inset case, rotation case) | re-computed independently; matches the committed assertions |
| Gesture state machine sequences (tap, slop, drag, cancel, orbit, pinch, wheel remainders, two-finger tap, pan, drag-not-stolen, disables, lifecycle release, pointer reorder) | executed against a line-by-line model of the Java engine; the committed `DrnasGestureEngineTest` expectations are the outputs of those runs |

## What CI verifies (workflow added in this change)

`native-touch-controls.yml` runs on push/PR and does what this environment cannot:

1. applies the whole chain to the pinned upstream commit;
2. proves both new patches reject a second application;
3. runs `verify_touch_controls.py`;
4. `javac` + runs **all** Android-free input tests, including the new ones;
5. compiles the patched `BlenderActivity.java` and every overlay class against
   `platforms;android-35/android.jar` — a real type check of the Android integration;
6. uploads the compiled classes as an artifact.

The existing native workflows now also run the new native patch, the verifier and the tests, so the
full-native build path is covered too.

## Not verified — requires a real device

These need the APK from the native CI build (or a repack) and physical hardware:

1. Select, drag, move/rotate/scale an object with a finger.
2. Orbit/pan/zoom without accidental selections (two-finger orbit needs the rebuilt native library;
   on a v7 repack it degrades to scrolling by design).
3. Right-click context menu from the RMB button and from the two-finger tap.
4. MMB press-and-hold drag, and MMB + LMB combinations.
5. Numeric transform entry through the Android keyboard (`G`, digits, `Enter`).
6. Text field editing, Backspace/Enter, focus switching between Blender fields.
7. File browser: folder clicks, scrolling, Open/Cancel while the overlay floats above it.
8. Reaching all four corners and both edges in direct and relative modes, including with Stretch on.
9. Toggling Stretch/fullscreen and confirming the pointer stays aligned.
10. Rotating the device, opening/closing the keyboard, dismissing dialogs and resuming without stuck
    buttons (the release paths are implemented and unit tested, but only hardware shows the real
    system-gesture and IME sequences).
11. Moving/resizing/hiding/restoring each group, then restarting to confirm persistence.
12. Physical mouse (hover, wheel, middle button) and hardware keyboard alongside the overlay.
13. Stylus hover and the S Pen side button.
14. Performance: the overlay only posts work on events (no polling, no per-frame logging); the wheel
    repeat is a 110 ms handler tick while a button is held. Frame-time impact was not measured.

## Known remaining blockers

* **Middle button, touch orbit, pan and the view keys require the rebuilt native library**
  (`libblender.so` built with `patch_native_ghost.py` + `patch_native_touch_extra.py`). The Java-only
  repack path keeps the v7 binary, is byte-identical as required, and consequently exposes only
  LMB/RMB/wheel/ASCII keys. Reproduction: build the repack, open the dock — MMB is absent and the
  navigation widget's view keys are shown disabled with "needs the native input update".
* **Direct-mode two-finger orbit is intentionally not implemented** (upstream's two-finger gesture
  also scrolls Blender's own panels; see `TOUCH_CONTROLS_NOTES.md`). Use the pad, the nav ORBIT pad
  or the MMB button.
* **Portrait support is not claimed.** Nothing in the overlay assumes landscape, and layouts are
  clamped on configuration changes, but Blender's own UI had not been validated in portrait before
  this change and still has not been.
