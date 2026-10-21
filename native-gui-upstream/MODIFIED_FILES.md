# Modified and added files

Everything below is either a deterministic patch script, an Android-free Java unit, a Java overlay
fragment injected by a patch script, or build/CI plumbing. Upstream Blender sources are untouched
except through `patch_native_ghost.py` (pre-existing) and `patch_native_touch_extra.py` (new).

## Added — Android-free logic (unit tested, compiled into the APK)

| File | Why |
|------|-----|
| `DrnasPointerMap.java` | Single view↔native coordinate strategy (`FIT` / `SUBRECT`), clamping, corner reach, cursor-sprite placement. Replaces three duplicated conversions. |
| `DrnasGestureEngine.java` | Pointer-id touch recogniser → real mouse input (tap/drag/orbit/pinch/pan/RMB), guaranteed press/release balance, slop and gesture classification, configurable features. |
| `DrnasRelativePad.java` | Relative pointer motion: speed, acceleration, sub-pixel accumulation, surface clamping, edge reach. |
| `DrnasMouseState.java` | Independent LMB/RMB/MMB state, momentary click vs. latch, `releaseAll()`, middle-button capability gating. |
| `DrnasShortcutStrip.java` | Persisted order and visibility of the shortcut strip, tolerant of stale or corrupt preferences, never hides the last button. |
| `DrnasControlLayout.java` | Group geometry/persistence model, safe-area clamping, six presets, tolerant decoding and migration of saved layouts. |
| `DrnasPointerMapTest.java`, `DrnasGestureEngineTest.java`, `DrnasRelativePadTest.java`, `DrnasMouseStateTest.java`, `DrnasControlLayoutTest.java`, `DrnasShortcutStripTest.java` | Automated tests: mapping arithmetic, every gesture transition, acceleration/edge reach, button independence/latching, persistence/clamping/presets. |

## Added — Android overlay fragments (injected, not compiled standalone)

| File | Why |
|------|-----|
| `drnas_touch_layer.java.inc` | Capability probe, single native emitter, gesture sink, mode switching + badge, new trackpad, physical-mouse path, pointer/cursor update, lifecycle release. |
| `drnas_dock_nav.java.inc` | Dock wheel buttons with hold-repeat, navigation widget (orbit/pan/zoom/frame/axis/persp), keyboard extras (backspace/arrows/hide/clear), layout clamping and presets. |

## Added — patch, verification and CI

| File | Why |
|------|-----|
| `patch_native_touch_extra.py` | Native extension: MMB (8/9), Shift hold (10/11), horizontal wheel (12), F1–F12/numpad/Home/End/PageUp/PageDown/punctuation keycodes, `nativeDrnasCapabilities()` JNI. Fail-closed anchors and self-checks. |
| `patch_drnas_touch_layer.py` | Java integration: removes the superseded trackpad/emitter/pointer mapping, inserts the new layer, adds MMB + wheel to the dock, nav widget and badge, lifecycle hooks, layout options and keyboard extras; installs the five logic classes into the APK tree. Fail-closed anchors + post-validation. |
| `verify_touch_controls.py` | Static contract gate across Java and C++ (action numbers, capability bits, dock completeness, keyboard reachability, release paths, single definitions, Android-free logic, test presence). |
| `run_input_tests.sh` | `javac`/`java` runner for all Android-free input tests. |
| `.github/workflows/native-touch-controls.yml` | Runs the full patch chain, the tests, the verifier and a real `javac` compile of the patched activity against `platforms;android-35/android.jar`. |
| `TOUCH_CONTROLS_NOTES.md` | The documented input contract (action numbers, gesture model, coordinate model, layout rules, keyboard rules). |
| `CHANGELOG_TOUCH_CONTROLS.md` | What changed and why, including the defects found in the baseline. |
| `TEST_REPORT.md` | What was verified here, what CI verifies, and what still needs a physical device. |

## Modified

| File | Why |
|------|-----|
| `.github/workflows/native-alpha-1.0-release.yml` | Repack path now applies the two new patches, verifies the control contract and runs the input tests. |
| `.github/workflows/native-gui-controls-build.yml` | Full native build applies the native extension right after `patch_native_ghost.py`, then the overlay chain, verification and tests. |
| `native-gui-upstream/verify_alpha_1_0.py` | The v10 assertion required the now-replaced `drnasNativePointerX` helper; it now asserts the stronger invariant (single mapping strategy through `DrnasPointerMap`, no second scaling path) so the stretch-aware behaviour is still enforced. |

## Unchanged on purpose

* Application identity, package names, storage paths and the separate DRNAS EMU installation.
* GPU driver selection, Vulkan/EGL setup, rendering resolution and aspect handling.
* Saved projects, save-before-exit flow, smooth zoom, splash, language picker.
* Native Blender binaries and driver blobs: neither patch touches any binary; the Java-only
  workflows keep them byte-identical (the existing repack verification still enforces that).
