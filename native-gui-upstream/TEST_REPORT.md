# Test report — rebuilt Blender mouse controls

**Status (2026-10-23):** the rebuilt ARM64 test APK is available for installation. Source integration,
JVM tests, Android Java compilation, APK signature, and native-input entrypoint checks passed. Visual
and real-device acceptance is still pending; no app screenshots or touch-device results are claimed.

## Get the build

Download the installable APK from the **Artifacts** section of [ARM64 APK workflow run 39906213207](https://github.com/MeYashverma/Blender/actions/runs/39906213207):

- Artifact name: `DRNAS-Touch-TEST-39906213207`
- Contains the signed ARM64 APK, `SHA256SUMS.txt`, and source attribution.
- Artifact expires 30 days after the workflow run.

The workflow verified the APK signature, ARM64 `libblender.so`, and the native touch-input and
capability-probe entrypoints before uploading it. Install on an ARM64 Android device. If Android
reports a signing conflict with an older test build, uninstall that build before installing this
one; uninstalling removes its app data.

## Current design

The controller is a movable Blender-dark group at the bottom-right with a broad relative trackpad, a
distinct wheel rail, and separate LMB/MMB/RMB controls. The trackpad supports pointer travel, LMB
tap, two-finger scroll, pinch zoom, and two-finger RMB. MMB is held explicitly to orbit. The group
has persisted sizing and placement, an edit-only move grip, safe-area clamping, and local vector
icons. The radial mouse ring, navigation dial, palette, and their gesture models are removed from
the maintained source set.

The direct-touch gesture path, physical mouse, keyboard/IME, Blender app identity, and unrelated GPU
paths remain on their existing bridges.

## Automated validation

- [ARM64 APK run 39906213207](https://github.com/MeYashverma/Blender/actions/runs/39906213207): clean pinned-upstream patch chain, release and touch verifiers, Android-free JVM suite, native ARM64 APK build, APK signature, and native input entrypoint inspection all passed.
- [Java validation run 39904810797](https://github.com/MeYashverma/Blender/actions/runs/39904810797): clean pinned-upstream patch chain, fail-closed rerun, touch verifier, JVM tests, and Android 35 Activity/overlay compilation passed.
- A later documentation-only update also passed the same Java validation in [run 39904889449](https://github.com/MeYashverma/Blender/actions/runs/39904889449).

The first APK attempt stopped in the release verifier because its expected English label still said
“Mouse and zoom”. The verifier was updated to “Mouse and touchpad”; the following complete APK run
passed.

## Still needs device acceptance

- Capture and review actual-app screenshots in landscape at representative phone/tablet sizes.
- Test pointer movement, wheel rail, scroll, pinch, LMB/MMB/RMB, latching, simultaneous touch,
  cancellation, rotation, keyboard/IME, physical mouse/keyboard, and Blender file-browser behavior.

These items require installing the artifact on a real touch device. Until then, visual and interaction
acceptance remain pending.
