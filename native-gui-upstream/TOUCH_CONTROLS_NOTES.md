# DRNAS touch control layer — input contract

This file is the single description of how a finger becomes a Blender mouse event. The Java,
the native bridge and the on-screen controls are checked against it by
`verify_touch_controls.py`, so the three cannot drift apart silently.

## Native action numbers (`nativeOnDrnasInput(action, x, y, value)`)

| action | meaning | Java sender |
|-------:|---------|-------------|
| 1 | cursor move to (x, y) in native space | `drnasSendInput(1, …)`, gesture sink |
| 2 / 3 | left button down / up | `drnasEmitMouse(LEFT, …)` |
| 4 / 5 | right button down / up | `drnasEmitMouse(RIGHT, …)` |
| 6 | vertical wheel, `value` in notches (fractional accumulates natively) | integrated dock scroll rail, pinch |
| 7 | touch gesture sensitivity (0.08 … 1.0) | SENS slider |
| 8 / 9 | **middle button down / up** (new) | `drnasEmitMouse(MIDDLE, …)`, orbit/pan |
| 10 / 11 | **Shift hold on / off** (new) | pan gesture, two-finger navigation-puck drag |
| 12 | **horizontal wheel** (new) | integrated dock scroll rail (sideways swipe) |

Actions 8–12 exist only after `patch_native_touch_extra.py`. The overlay asks
`nativeDrnasCapabilities()` which of them the running `libblender.so` supports and disables (with a
visible reason) anything it cannot deliver. On an older library the symbol is missing, Java catches
the `UnsatisfiedLinkError` and behaves exactly like Alpha 1.0 plus the fixed pointer mapping.

Capability bits (identical constants in `GHOST_AndroidMain.cc` and `BlenderActivity.java`):

| bit | value | meaning |
|----:|------:|---------|
| MIDDLE | 1 | actions 8/9 available |
| SHIFT | 2 | actions 10/11 available |
| HSCROLL | 4 | action 12 available |
| KEYS | 8 | F1–F12, numpad, Home/End/PgUp/PgDn, punctuation reach GHOST |
| GESTURES | 16 | the touch gesture layer is present in this build |

## Gesture model (relative / PAD mode, `DrnasGestureEngine`)

| gesture | emitted mouse input |
|---------|---------------------|
| one-finger tap | LMB press + release **at the landing point** |
| one-finger drag | LMB down at the landing point (after the touch slop), pointer follows, LMB up |
| two-finger drag | MMB down, pointer follows the centroid, MMB up → orbit |
| pinch in / out | wheel notches → zoom |
| three-finger drag | Shift + MMB down → pan |
| two-finger tap | RMB press + release → context menu |

Rules the tests enforce:

* the press is reported where the finger landed, never where it has already travelled;
* every press gets exactly one release, including after `ACTION_CANCEL`, `onPause`, `onStop` and
  loss of window focus;
* a gesture never also produces a click;
* fingers are tracked by **pointer id**, so an Android pointer-index reorder cannot swap buttons or
  jump the cursor;
* wheel remainders are kept, and a single event is capped at ±4 notches so the native ±8 clamp can
  never clip a fast pinch;
* when MMB is unavailable, two-finger drag degrades to vertical scroll instead of doing nothing.

Direct-touch mode is deliberately left to the upstream GHOST gesture code (tap = click, two-finger
= pan/zoom, three-finger = pan, with the existing DRNAS sensitivity scaling). Upstream decides
pan-versus-zoom from the geometry and, crucially, its two-finger scroll is what makes Blender's own
file browser and panels scroll. Replacing that with an unconditional orbit would break panel
scrolling in editors the overlay cannot reason about, so orbit is offered through the pad and the
navigation widget instead. This is a deliberate scope decision, not an oversight.

## Reimplemented touch-control hierarchy

The mouse is one custom-drawn, single hit surface instead of detached per-button cards and four
wheel keys. The upper 48 dp rail emits vertical wheel input with an upward/downward gesture and,
when `DRNAS_CAP_HSCROLL` is present, horizontal wheel input with a sideways gesture. The lower
three zones are LMB / MMB / RMB, each with independent press/release and double-tap latch/unlock
state. Each touch zone remains at least 48 dp high; the dock state model tracks pointer ownership,
cancel and lifecycle release. MMB is visibly disabled when the loaded native library lacks its
capability. A physical mouse, native viewport and keyboard paths remain unchanged.

Navigation is a separate compact, movable orientation puck: a tap chooses an axis view, a one-finger
drag emits MMB orbit, and a two-finger drag emits Shift+MMB pan. Compact zoom-in, zoom-out and frame
actions sit beside it. Standard view shortcuts are in a hidden-by-default expandable row rather than
a permanent button wall. The Android-free `DrnasNavGestureTest` and `DrnasDockGestureTest` exercise
the new interaction state machines; the Android views themselves still need CI compilation and
actual-device touch/screenshot review.

## Coordinate model (`DrnasPointerMap`)

Two rectangles exist and are not the same after stretch/rotation/IME:

* **view space** — the overlay window/popup that receives the finger;
* **native space** — the GHOST surface that `nativeOnDrnasInput` addresses.

The pointer is stored in native space only. The cursor sprite is drawn back through
`viewX/viewY`, so what the user sees and what Blender receives cannot disagree. `MODE_FIT` maps one
rectangle onto the other uniformly (the popup covers the whole surface); `MODE_SUBRECT` maps 1:1
with an offset (a pad that shrank because the keyboard is up). Clamping happens once, before the
scale, and the mapping is monotonic, so every native pixel — including all four corners and the
last addressable row/column — is reachable without the cursor leaving the display.

## Layout model (`DrnasControlLayout`)

Every group keeps an independent offset, size, button scale, opacity, visibility and expanded flag.
`clampOffset`/`clampGroup` guarantee that at least `MIN_VISIBLE_DP` (56 dp) of a group stays inside
the safe area after a rotation, a resize, a cutout or a system-gesture inset change. Presets write
all groups at once so a user never has to mirror controls by hand, and the desktop preset hides the
overlay without destroying the saved layout.

## Keyboard

Normal text entry still goes through `nativeOnCommitText` (IME composition, Unicode, emoji) and the
existing `nativeOnKey` path for Backspace/Enter — the overlay never fakes text with key presses.
Modifiers are momentary on press-and-hold, latched on double-tap, and released by "Clr". The new
arrow/Backspace/"Kb ✕" keys call the same bridge, and `showKeyboard()`/`hideKeyboard()` keep Android
focus behaviour unchanged.

## Shortcut strip contract

* Every shipped button can be moved earlier/later and hidden from the settings panel; the order and
  the hidden set are stored in the `shortcut_strip` preference as `order|hidden`.
* Decoding is tolerant: labels from another build are dropped, labels added by a newer build are
  appended, duplicates collapse, and at least one button always stays visible.
* The strip itself is not rebuilt - the same `Button` objects are reordered inside their rows, so
  key bindings, latches and listeners survive an arrangement change.

## Gesture switches

`gesture_orbit`, `gesture_pinch`, `gesture_pan` and `gesture_rmb` each enable one recogniser, and
`gesture_sensitivity` (50-200 %) scales the touch slop; `zoom_rate` sets wheel notches per pinch
pixel and `pointer_acceleration` sets the extra speed for fast swipes. A capability that the
running library cannot provide disables its switch content, it never silently fakes the gesture.

## While a text field is being edited

If the soft keyboard is on screen, the navigation widget's keys (frame, axis, perspective) refuse
to fire and flash a short refusal instead. A numpad key would otherwise be typed into Blender's own
text field. The gesture pads and the pointer keep working, because moving the mouse is exactly what
a text field expects.
