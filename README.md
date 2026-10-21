# DRNAS Blender Touch Studio — source/build branch

This branch replaces the old debug-looking floating buttons with a fresh Android-native touch-control surface for the pinned DRNAS Native Blender pre-alpha baseline.

## New control design

- A slim pinned header with layout-edit, settings, and hide/show controls.
- Dedicated relative cursor trackpad plus LMB/RMB buttons. Move the Blender cursor with the trackpad, hold either mouse button while moving for click-and-drag, or double-tap to latch a button.
- Compact horizontally scrolling tool dock: Move (G), Rotate (R), Scale (S), Tab/mode, Escape, Undo/Redo, and quick keys.
- Separate viewport panel for wheel zoom and view navigation (front/right/top/perspective/orbit).
- Landscape-adaptive dock sizing, configurable control size and opacity, configurable cursor/gesture sensitivity, and saved panel positions.
- Edit-layout mode; drag each panel by its header grip. In regular use, header grips do not intercept viewport gestures.
- Lifecycle cleanup releases latched/held virtual mouse buttons when the activity backgrounds.

The viewport itself stays native: this overlay does not synthesize a new orbit/pan implementation or replace the existing GHOST gesture path. It uses the existing JNI bridge for virtual mouse buttons, wheel input, and gesture sensitivity.

## Build

The DRNAS Touch Studio Android APK workflow downloads the public PreAlpha 0.1 ARM64 APK, verifies its native input JNI exports, applies the new Activity overlay to the pinned simfeo/blender source, compiles the Java/UI layer, repackages it with the existing native libraries/runtime unchanged, and validates the final APK signature and package ID.

The workflow is intentionally a Java/UI-only repack; it does not claim to rebuild Blender's C/C++ native library. CI tests the mouse-button state machine and asserts every packaged lib/ and assets/ entry is byte-identical to the baseline.

## Install and test

When the workflow completes, open Actions → DRNAS Touch Studio Android APK, download the DRNAS-Blender-Touch-Studio artifact, extract it, and install DRNAS-Blender-Touch-Studio-arm64.apk.

Test with a disposable scene:
1. Tap G/R/S and confirm Blender enters the transform operation.
2. Drag on the cursor trackpad to move the Blender cursor. Hold LMB/RMB while moving the trackpad to test click-and-drag; double-tap a mouse button to latch it and tap once to release.
3. Try mouse-wheel zoom, view presets, Undo/Redo, quick keys, keyboard popup, edit-layout dragging, hide/show, size/opacity/sensitivity, and background/resume.
4. Save a copy of any .blend file before testing.

## Limits

- This is an experimental source patch and CI build workflow, not physical-device validation.
- The base APK is public PreAlpha 0.1 and the native binary remains unchanged; the workflow validates existing JNI integration before compiling UI code.
- Every CI run is debug-signed with a new key; Android may require uninstalling a previous Touch Studio test build before installing a newer one. Keep the original app and back up projects.
- Blender remains subject to upstream GPL terms and notices.
