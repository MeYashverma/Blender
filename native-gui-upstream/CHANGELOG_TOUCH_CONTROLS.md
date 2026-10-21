# Changelog — DRNAS native touch & control system

Baseline: `release/native-alpha-1.0` (5dad4dec8dd224523f8618ab7bb1e4343c94c446) of
Frane12/DRNAS-Native-Blender, which is a strict superset of every feature/fix branch in that
repository (v8 editor, v9 pointer+IME, v10 floating windows/stretch). Upstream Blender source stays
pinned at `simfeo/blender` `d93bd33cba768e19ee4387a31c0ea137ee2f0047`.

## Confirmed defects fixed

1. **Dead shortcut keys.** `convertAndroidKey()` in `GHOST_SystemAndroid.cc` maps only A–Z, 0–9,
   space, enter, delete, backspace, tab, escape and the D-pad. It returns `GHOST_kKeyUnknown` for
   every function key and every numpad key, and `dispatchJavaKeyEvent()` drops those silently. The
   shipped "Search" (F3), "Front" (Numpad 1), "Side" (Numpad 3) and "Top" (Numpad 7) buttons
   therefore did nothing at all. Numpad, F1–F12, Home/End/PageUp/PageDown and punctuation are now
   mapped, so those buttons work and the new navigation widget can frame the view.
2. **No middle mouse button anywhere.** Blender orbits with MMB and the overlay never produced one,
   so orbit was impossible without a physical mouse. Actions 8/9 now emit a real
   `GHOST_kButtonMaskMiddle` press/release, and the dock, the gestures and the nav pad all drive it.
3. **Two independent pointer-mapping paths.** The old overlay clamped the pointer to the touch
   popup's bounds and then converted with a separate ratio function, while the cursor sprite was
   positioned with a third calculation. After stretch, rotation or a keyboard inset, the pad and the
   GHOST surface are different rectangles, so the pointer could not reach the surface edges in the
   smaller window and the arithmetic was duplicated in three places. Replaced by one tested model
   (`DrnasPointerMap`) with an explicit `FIT` (uniform) and `SUBRECT` (1:1 + offset) mode; the
   pointer is stored in native coordinates only and the sprite is derived from them.
4. **`DrnasPointerMap` returned NaN before the window was measured.** An unmeasured view is 1 px
   wide, so the fit conversion divided by a zero span. Any code that ran before the first layout
   pass would have placed the pointer at NaN. The map is now inert until both rectangles are known.
5. **A keyword used as an identifier.** `float native = ...` in a test is rejected by `javac`
   because `native` is a modifier. Caught by the new CI compile, and the local verifier now scans
   declarations for reserved words so it cannot recur.
6. **Nothing released held input on focus loss.** `onPause` released LMB/RMB only; a latched button
   or an in-flight gesture survived a dialog, the notification shade or a system gesture. Every
   held button/modifier is now released on `onPause`, `onStop`, focus loss, `ACTION_CANCEL` and
   mode switches.

## New behaviour

### Input
* `DrnasGestureEngine` — pointer-id based recogniser: tap = LMB click at the landing point, drag =
  LMB drag (touch slop), two-finger drag = MMB orbit, pinch = wheel zoom, three-finger drag =
  Shift+MMB pan, two-finger tap = RMB. A gesture never also clicks, and every press is released
  exactly once (cancel, pause, stop, focus loss, mode switch).
* `DrnasRelativePad` — touchpad motion with base speed, optional acceleration, sub-pixel
  accumulation (a slow 0.25× swipe still adds up), clamped to the surface, corners reachable.
* `DrnasMouseState` — independent LMB/RMB/MMB state, momentary clicks, double-tap latching,
  `releaseAll()`, and middle-button gating.
* `DrnasPointerMap` — one coordinate strategy (see defect 3).
* Physical mouse/stylus: hover, wheel, horizontal wheel and all three buttons still go through the
  same bridge; the touch overlay does not interfere.

### On-screen controls
* Mouse dock: LMB, RMB, MMB (only shown when the running library supports it), wheel up/down with
  press-and-hold repeat, horizontal wheel where available, latched state marked with a dot.
* Navigation widget: draggable and collapsible; ORBIT and PAN drag pads (Shift+MMB pan), zoom −/+,
  Frame Selected, Frame All, Front/Right/Top, Ctrl variants Back/Left/Bottom, Perspective/Ortho,
  User view. Keys that a build cannot deliver are visibly disabled with a reason.
* Mode badge: `TOUCH ○` / `PAD ●`, tap to switch; shows ORBIT/PAN/ZOOM while a gesture is active;
  long-pressable restore point when overlays are hidden.
* Keyboard strip: Backspace, four arrows, "Kb ✕" (hide the Android keyboard), "Clr"
  (release every latched key and button). Modifier keys keep press-and-hold = momentary,
  double-tap = latch, tap = unlock. Text entry still uses `nativeOnCommitText`.
* Shortcut strip: every shipped button can be moved earlier/later and shown/hidden from the
  settings panel, persisted, with a reset; the shipped list can never be hidden completely.
* Gesture switches: two-finger orbit, pinch zoom, three-finger pan and two-finger-tap right click
  can each be disabled independently, and a sensitivity slider changes how far a finger must travel
  before a gesture starts (so accidental drags can be tuned out).
* View and frame keys refuse to fire while the Android keyboard is up: a numpad key would otherwise
  be typed into Blender's own text field instead of framing the view.
* Layout: six presets (two thumbs, one hand, compact, large targets, tablet, external
  mouse+keyboard), reset, hide/show, per-group geometry/opacity, and automatic clamping so a group
  can never end up unreachable after a rotation, resize, cutout or system-gesture inset change.

### Build / patch architecture
* `patch_native_touch_extra.py` — deterministic native extension (actions 8–12, extended keycodes,
  `nativeDrnasCapabilities()`), anchored with `once()` assertions and self-checks.
* `patch_drnas_touch_layer.py` — deterministic Java integration; every anchor must occur exactly
  once, superseded code must disappear, and the six logic classes are installed into the APK tree.
* `verify_touch_controls.py` — cross-language contract gate (Java action numbers vs. native
  dispatch, capability bits on both sides, dock/keyboard completeness, release paths, one definition
  of each lifecycle method, Android-free logic classes, hosted tests).
* `run_input_tests.sh` — compiles and runs every Android-free input test with plain `javac`/`java`.
* New workflow `native-touch-controls.yml` runs the whole chain, the tests, the verifier and a real
  `javac` compile of the patched activity against `android.jar`.

## Design decisions worth knowing

* **Direct-touch mode still uses upstream GHOST gestures.** Upstream's two-finger gesture is
  pan-or-zoom and is also what scrolls Blender's file browser, panels and lists. Turning every
  two-finger drag into an orbit from the native layer would break those editors, which the overlay
  cannot inspect. Orbit is therefore offered by the pad (relative mode), the nav widget, and the
  MMB dock button — all context-aware.
* **Capability probing instead of pretending.** A Java-only repack of the previously accepted v7
  APK keeps that APK's `libblender.so`, which has no middle button and no extended keys. The probe
  returns 0 there, so the MMB button is absent, the view keys are disabled, and orbit gestures fall
  back to scrolling — with the reason documented rather than a button that does nothing.
* **No separate overlay.** Everything is added to the existing panel/popup infrastructure and the
  existing widgets/listeners, so there is never a second copy of a control underneath.
