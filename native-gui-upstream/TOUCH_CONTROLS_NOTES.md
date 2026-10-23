# DRNAS touch controls — rebuilt mouse-controller contract

This document describes the current native Android patch pipeline. The mouse ring, navigation dial,
radial palette, and their gesture models are removed from the maintained source set. The replacement
is a Blender-dark floating mouse controller with a broad relative trackpad, a dedicated wheel rail,
and separate LMB/MMB/RMB targets. This is an implementation contract, not visual or device acceptance:
that still requires an Android build, screenshots, and real-touch testing.

## Native input bridge

`nativeOnDrnasInput(action, x, y, value)` keeps the existing Blender/GHOST input path. The controller
adds no separate Blender process, app identity, storage area, renderer, or GPU behavior.

| action | meaning | controller use |
|-------:|---------|----------------|
| 1 | move pointer to `(x, y)` in native space | relative trackpad movement |
| 2 / 3 | LMB down / up | dedicated LMB target or trackpad tap |
| 4 / 5 | RMB down / up | dedicated RMB target or two-finger tap |
| 6 | vertical wheel, fractional notches accumulated natively | wheel rail, two-finger scroll, pinch zoom |
| 7 | existing touch-gesture sensitivity input | compatibility with the upstream gesture bridge |
| 8 / 9 | MMB down / up | dedicated MMB target, viewport orbit |
| 10 / 11 | Shift hold on / off | existing three-finger pan gesture |
| 12 | horizontal wheel | horizontal two-finger scroll when supported |

The JNI capability probe disables controls the loaded native library cannot deliver. A physical
mouse continues through the native path, including hover, wheel and middle-button events. The input
release path clears held buttons and modifiers on cancel, focus loss, pause, stop and exit.

## Rebuilt mouse controller

The controller is one movable, persisted landscape group:

- **Trackpad:** one-finger travel moves the Blender pointer; tap clicks LMB; two-finger vertical or
  horizontal travel scrolls; pinch zooms; two-finger tap clicks RMB.
- **Wheel rail:** a distinct, narrow rail provides direct vertical wheel scrolling without requiring
  a gesture.
- **Buttons:** separate, always-visible LMB, MMB and RMB views. Each has independent pointer
  ownership, visible pressed state and a minimum target size; press-and-hold emits down/up and a
  deliberate double-tap latches/unlatches.
- **Orbit:** hold MMB while moving the trackpad. The middle target is visibly disabled if the native
  library lacks MMB support.
- **Edit mode:** only then does a temporary grip appear. Dragging the grip moves the whole group;
  size, opacity, visibility and position remain persisted. Safe-area clamps keep a useful portion of
  the group reachable after rotation, resizing, cutouts or system insets.

First-run migration removes stale radial-tool positions, restores a clean controller layout, collapses
keyboard shortcuts, and selects relative-pointer mode so the trackpad has an immediately visible
cursor. Later preference changes are not overwritten.

The separate direct-touch mode remains available for users who prefer to touch Blender's viewport
and panels directly. Its existing GHOST gesture routing is retained rather than forcing viewport
orbit over file-browser or panel scrolling. The full-screen input layer also preserves physical
mouse/hover input; it is not a replacement for the new docked trackpad.

## Gesture and ownership models

`DrnasMousePadGesture` is Android-free and owns trackpad recognition, pointer IDs, scroll/pinch
remainders, tap decisions, and cancellation. `DrnasMouseButtonModel` is Android-free and owns each
button's independent touch owners and latching state. Their Android views render feedback and call
the existing Activity/native bridge; neither model directly depends on Android.

The retained `DrnasGestureEngine` covers the separate direct-touch mode and native gesture bridge.
It is not the new dock's mouse controller. Both paths must release every held input, and a gesture
must not also produce a click after it has been classified as a drag or scroll.

## Layout and visual design

Only the mouse controller and keyboard/shortcut shelf are rendered touch-control groups. The
presets, safe-area checks, and saved sizes apply to those groups; there are no navigation or radial
palette groups. The visual layer uses local vector icons, Blender-dark surfaces, quiet idle opacity,
clear pressed feedback, and no emoji as primary control icons. Mouse hit targets retain a 48 dp
minimum; compact presets may reduce surrounding spacing but not the button touch area.

## Physical input and keyboard

The physical mouse, Blender viewport, GPU paths, and Android keyboard/IME remain on their existing
bridges. The shortcut shelf remains editable and persisted, and includes Blender view commands for
front/side/top/perspective, frame selected, and frame all. Text entry uses the existing IME commit
path rather than faking Unicode with key presses.

## Validation gates

The clean pinned-upstream patch chain and `verify_touch_controls.py` validate integration anchors,
Java/native action contracts, absence of superseded radial classes, lifecycle release paths, and
keyboard mappings. `run_input_tests.sh` exercises Android-free models. Neither static validation nor
JVM tests establishes layout quality on a device. Before calling this accepted, build the Android
APK, capture screenshots at representative landscape sizes, and test simultaneous touch, edge
scroll, pinch, cancellation, button latching, physical peripherals, rotation and keyboard/IME on a
real touch device.

## Research references

The interaction pattern borrows useful control-system ideas from [PojavLauncher custom controls](https://pojavlauncher.app/wiki/getting_started/CUSTOM-CONTROLS.html)
(editable layouts, independent buttons and presets), without copying its Minecraft-specific map or
visual skin. Blender's [Beyond Mouse & Keyboard](https://code.blender.org/2025/07/beyond-mouse-keyboard/)
notes task-oriented floating controls and reduced density, while the [Blender hardware manual](https://docs.blender.org/manual/en/latest/getting_started/configuration/hardware.html)
explains why the three mouse buttons and wheel are important.
