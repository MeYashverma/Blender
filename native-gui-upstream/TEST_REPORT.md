# Test report — rebuilt Blender mouse controls

**Status:** implementation and source-level integration are validated; screenshot/device acceptance is
still pending. This report supersedes earlier radial-control reports. The ring, navigation dial,
radial palette, and their gesture models have been removed from the maintained source set.

## Current design

The controller is a movable Blender-dark group with a broad relative trackpad, a distinct wheel rail,
and separate LMB/MMB/RMB controls. The trackpad supports pointer travel, LMB tap, two-finger scroll,
pinch zoom, and two-finger RMB. MMB is held explicitly to orbit. Layout position, size, visibility and
opacity are persisted; the edit grip appears only in edit mode. The legacy full-screen gesture path is
kept as a separate optional input mode, as are the physical mouse, keyboard, Blender viewport and GPU
paths.

## Automated validation

GitHub Actions run **39904381799** passed on commit
`5e721434c4ed58249de27a922a1a4c06c70ddebf`:

- Clean patch chain against pinned upstream `simfeo/blender@d93bd33cba768e19ee4387a31c0ea137ee2f0047`.
- Fail-closed rerun check and `verify_touch_controls.py`.
- Android-free JVM suite, including the new trackpad and independent mouse-button models.
- `javac` compilation of the patched Android Activity and overlay classes against Android 35.

The first CI attempt exposed that the two new default-package tests were invoked using a packaged class
name. The runner was corrected; the next run passed all JVM tests. The following compile pass exposed
and fixed stale navigation-speed state and a type mismatch between new custom views and the legacy
`TextView` sizing map.

The local workspace has no `javac`; the successful JVM and Android Java compile results above are from
CI. The clean pinned patch chain and static verifier were also run locally and passed.

## Not yet accepted

- No APK build or install was performed for this mouse-controller revision.
- No actual-app screenshots have been captured or reviewed.
- No real-touch device tests were run for multitouch, button latching, rotation, keyboard/IME,
  physical peripherals, edge scrolling, or cancellation.

Therefore visual quality and device interaction are **not yet accepted**. The app still needs an APK,
landscape screenshots at representative sizes, and real-touch validation before those claims are made.
