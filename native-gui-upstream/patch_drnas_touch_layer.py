#!/usr/bin/env python3
"""Integrate the DRNAS touch control layer into the pinned BlenderActivity.java.

Run **after** the existing overlay chain (``apply_gpu_test_patches.sh`` plus
``add_control_group_editor.py``, ``fix_relative_ime_input.py``,
``fix_floating_stretch_v10.py``, ``patch_alpha_1_0_uilib.py``).

Deterministic and fail closed: every anchor must occur exactly once, every fragment is inserted
verbatim from a ``.java.inc`` file, and the script re-reads the result to prove the old,
double-scaling trackpad and the old single "mouse down" bookkeeping are gone. An upstream or
overlay change that moves an anchor stops the build with the anchor named, instead of producing a
subtly broken overlay.

GPL-2.0-or-later, like the upstream sources this patches.
"""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("usage: patch_drnas_touch_layer.py <upstream-source-root>")

root = Path(sys.argv[1])
patch_dir = Path(__file__).resolve().parent
activity = root / "build_files/android/apk/app/src/main/java/org/blender/blender/BlenderActivity.java"

if not activity.is_file():
    raise SystemExit(f"missing {activity}")


def load(name: str) -> str:
    path = patch_dir / name
    if not path.is_file():
        raise SystemExit(f"missing fragment {path}")
    return path.read_text()


touch_layer = load("drnas_touch_layer.java.inc")
dock_nav = load("drnas_dock_nav.java.inc")

s = activity.read_text()


def once(old: str, new: str, what: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{what}: expected exactly one anchor, found {count}:\n{old[:200]}")
    s = s.replace(old, new, 1)


def cut(start_marker: str, end_marker: str, what: str, replacement: str = "") -> None:
    """Replaces everything between two markers (exclusive of the end marker)."""
    global s
    if s.count(start_marker) != 1:
        raise SystemExit(f"{what}: start marker count {s.count(start_marker)}")
    if s.count(end_marker) != 1:
        raise SystemExit(f"{what}: end marker count {s.count(end_marker)}")
    start = s.index(start_marker)
    end = s.index(end_marker, start)
    s = s[:start] + replacement + s[end:]


# ------------------------------------------------------------------ 1. native declaration
once(
    "  private native void nativeOnDrnasInput(int action, float x, float y, float value);",
    "  private native void nativeOnDrnasInput(int action, float x, float y, float value);\n"
    "  /* DRNAS capability probe: missing on an older libblender.so, caught in Java. */\n"
    "  private native int nativeDrnasCapabilities();",
    "native capability declaration",
)

# ------------------------------------------------------------------ 2. remove superseded code
# The old emitter/bookkeeping, the double-scaling pointer mapping and the old trackpad are all
# replaced by the new layer, which is inserted in step 3.
once(
    """  private void drnasSendInput(int action, float x, float y, float value) {
    if (action == 2 || action == 4) {
      drnasClickEffectUntil = android.os.SystemClock.uptimeMillis() + 180;
      if (drnasPointer != null) drnasPointer.postInvalidateDelayed(190);
    }
    if (action == 2) drnasHeldMouseButtons |= 1;
    if (action == 3) drnasHeldMouseButtons &= ~1;
    if (action == 4) drnasHeldMouseButtons |= 2;
    if (action == 5) drnasHeldMouseButtons &= ~2;
    if (action == 6 && drnasSmoothZoomEnabled && drnasSmoothZoomReady) {
      drnasOfferZoom(value);
    } else nativeOnDrnasInput(action, x, y, value);
    if (action >= 2 && action <= 5) drnasUpdatePointer();
  }

""",
    "",
    "old drnasSendInput",
)

cut(
    "  private float drnasNativePointerX(float x) {",
    "  private final class DrnasPointer extends View {",
    "old pointer mapping and trackpad",
)

# ------------------------------------------------------------------ 3. insert the new layer
once(
    "  private void drnasDockMouseButtons(LinearLayout dock) {",
    touch_layer + "\n" + dock_nav + "\n  private void drnasDockMouseButtons(LinearLayout dock) {",
    "touch layer insertion",
)

# ------------------------------------------------------------------ 4. dock: middle + wheel
once(
    '    for (String key : new String[] {"lmb", "rmb"}) {',
    """    final java.util.ArrayList<String> dockKeys = new java.util.ArrayList<>();
    dockKeys.add("lmb");
    dockKeys.add("rmb");
    if (drnasHasCapability(DRNAS_CAP_MIDDLE)) {
      dockKeys.add("mmb");
    }
    for (String key : dockKeys) {""",
    "dock button list",
)
once(
    """      drnasDockedMouse.put(button, dock);
    }
  }""",
    """      drnasDockedMouse.put(button, dock);
    }
    drnasDockExtraButtons(dock);
  }""",
    "dock extra buttons",
)

# ------------------------------------------------------------------ 5. dock buttons + setup
once(
    """    drnasMouse("LMB", 2, 3, true, "lmb");
    drnasMouse("RMB", 4, 5, false, "rmb");""",
    """    drnasSetupTouch();
    drnasMouse("LMB", 2, 3, true, "lmb");
    drnasMouse("RMB", 4, 5, false, "rmb");
    drnasMouse("MMB", 8, 9, false, "mmb");""",
    "mouse button creation",
)

# ------------------------------------------------------------------ 6. physical mouse path
once(
    """      // Hardware mouse buttons/hover still use the same native input bridge.
      drnasMousePad.setOnHoverListener((v, e) -> { drnasMovePointer(e.getX(), e.getY()); return true; });
      drnasMousePad.setOnGenericMotionListener((v, e) -> {
        drnasMovePointer(e.getX(), e.getY());
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
          drnasSendInput(6, 0, 0, e.getAxisValue(MotionEvent.AXIS_VSCROLL));
          return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS
            || e.getActionMasked() == MotionEvent.ACTION_BUTTON_RELEASE) {
          boolean left = (e.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0;
          boolean right = (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
          if (left != ((drnasHeldMouseButtons & 1) != 0)) drnasSendInput(left ? 2 : 3, 0, 0, 0);
          if (right != ((drnasHeldMouseButtons & 2) != 0)) drnasSendInput(right ? 4 : 5, 0, 0, 0);
          return true;
        }
        return false;
      });""",
    """      // Hardware mouse, hover, wheel and stylus share one capability-aware path so a
      // physical mouse keeps its middle button, hover and horizontal scroll.
      drnasMousePad.setOnHoverListener((v, e) -> drnasHandleMouseEvent(e));
      drnasMousePad.setOnGenericMotionListener((v, e) -> drnasHandleMouseEvent(e));""",
    "physical mouse path",
)

once(
    """    if (!drnasMouseWindow.isShowing()) drnasMouseWindow.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, 0, 0);
    if (drnasPointerX < 0) drnasMovePointer(decor.getWidth() / 2f, decor.getHeight() / 2f);
    drnasUpdatePointer();""",
    """    drnasSyncPointerGeometry();
    if (!drnasMouseWindow.isShowing()) drnasMouseWindow.showAtLocation(decor, Gravity.TOP | Gravity.LEFT, 0, 0);
    if (drnasPointerX < 0f) drnasMovePointer(decor.getWidth() / 2f, decor.getHeight() / 2f);
    drnasUpdatePointer();""",
    "mouse layer geometry sync",
)

# ------------------------------------------------------------------ 7. mode switch in settings
once(
    """    mode.setOnCheckedChangeListener((v, enabled) -> {
      drnasSendInput(3, 0, 0, 0); drnasSendInput(5, 0, 0, 0);
      drnasReleaseAllInputs();
      drnasRelativeMouse = enabled;
      drnasPointerX = drnasPointerY = -1;
      drnasPrefs.edit().putBoolean("relative_mouse", enabled).apply();
      drnasHidePanels();
      drnasRefreshPanels();
    });""",
    """    mode.setChecked(drnasInputMode == DRNAS_MODE_RELATIVE);
    mode.setOnCheckedChangeListener((v, enabled) -> {
      drnasSetInputMode(enabled ? DRNAS_MODE_RELATIVE : DRNAS_MODE_DIRECT, true);
      v.setChecked(drnasRelativeMouse);
    });""",
    "input mode switch",
)

# Keep one source of truth for the mode: the persisted int, not a second boolean.
once(
    """    drnasRelativeMouse = drnasPrefs.getBoolean("relative_mouse", false);""",
    """    drnasRelativeMouse = drnasInputMode == DRNAS_MODE_RELATIVE;""",
    "mode preference source",
)

# ------------------------------------------------------------------ 8. keyboard extras
once(
    """    final Button keyboard = drnasSmallKey("Keyboard", 0, 0, bottom);
    keyboard.setOnClickListener(v -> showKeyboard());""",
    """    final Button keyboard = drnasSmallKey("Keyboard", 0, 0, bottom);
    keyboard.setOnClickListener(v -> showKeyboard());
    installDrnasKeyExtras(bottom);""",
    "keyboard extras hook",
)

# ------------------------------------------------------------------ 9. settings: layout presets
once(
    "    installDrnasMouseOptions(optionRows);",
    "    installDrnasMouseOptions(optionRows);\n    installDrnasLayoutOptions(optionRows);",
    "layout options hook",
)

# ------------------------------------------------------------------ 10. overlay install
once(
    "    installDrnasShortcutStrip();\n    installDrnasAndroidBack();",
    "    installDrnasShortcutStrip();\n    installDrnasNavWidget();\n"
    "    installDrnasModeBadge();\n    installDrnasAndroidBack();",
    "navigation widget install",
)

# ------------------------------------------------------------------ 11. lifecycle
# The activity already implements onWindowFocusChanged (immersive re-apply): extend it instead of
# defining a second copy, which would not compile.
once(
    """    if (hasFocus) {
      enterImmersive();
      drnasRefreshPanels();
    }
  }""",
    """    if (hasFocus) {
      enterImmersive();
      drnasRefreshPanels();
    }
    else {
      /* A dialog, the notification shade or a system gesture took the input focus: any held
       * button belongs to a gesture that will never receive its UP. */
      drnasReleaseTouchInput("focus-lost");
      drnasReleaseAllInputs();
      drnasStopRepeat();
    }
  }""",
    "focus loss release",
)

once(
    """  @Override protected void onPause() {
    drnasReleaseAllInputs();""",
    """  @Override protected void onStop() {
    drnasReleaseTouchInput("stop");
    drnasReleaseAllInputs();
    drnasStopRepeat();
    super.onStop();
  }

  @Override protected void onPause() {
    drnasReleaseAllInputs();""",
    "onStop release",
)

once(
    """    // Explicit release to avoid stuck virtual buttons when Android backgrounds Blender.
    drnasSendInput(3, 0f, 0f, 0f);
    drnasSendInput(5, 0f, 0f, 0f);""",
    """    // Release every touch-driven button and modifier, not just LMB/RMB.
    drnasReleaseTouchInput("pause");
    drnasStopRepeat();""",
    "pause release",
)

once(
    """  @Override public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    getWindow().getDecorView().post(() -> drnasRefreshPanels());""",
    """  @Override public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    getWindow().getDecorView().post(() -> {
      drnasSyncPointerGeometry();
      drnasClampLayout();
      drnasRefreshPanels();
    });""",
    "configuration change clamp",
)

# ------------------------------------------------------------------ 12. drag persistence compatibility
# Dragging a group writes the legacy keys; keep the two views of the same state in sync so a
# preset or a clamp never resurrects a stale offset.
once(
    """        case MotionEvent.ACTION_UP:
          drnasPrefs.edit().putFloat(name + "_x", drnasOffsetX(target))
              .putFloat(name + "_y", drnasOffsetY(target)).apply();
          return true;""",
    """        case MotionEvent.ACTION_UP:
          drnasPrefs.edit().putFloat(name + "_x", drnasOffsetX(target))
              .putFloat(name + "_y", drnasOffsetY(target)).apply();
          drnasClampPanel(target, name, drnasLayoutBounds());
          return true;""",
    "drag persistence",
)

# ------------------------------------------------------------------ 13. validation
result = s
required = [
    ("DrnasGestureEngine", "gesture engine"),
    ("DrnasPointerMap", "pointer map"),
    ("DrnasMouseState", "mouse state"),
    ("DrnasRelativePad", "relative pad"),
    ("DrnasControlLayout", "layout model"),
    ("private native int nativeDrnasCapabilities();", "capability declaration"),
    ("drnasSetInputMode(", "mode switching"),
    ("installDrnasNavWidget(", "navigation widget"),
    ("installDrnasLayoutOptions(", "layout presets"),
    ("drnasReleaseTouchInput(", "input release"),
    ('dockKeys.add("mmb")', "middle button in the dock"),
    ("drnasHasCapability(DRNAS_CAP_MIDDLE)", "middle button capability gate"),
]
for needle, what in required:
    if needle not in result:
        raise SystemExit(f"validation failed: missing {what}")

forbidden = [
    "drnasNativePointerX",
    "private final class DrnasTrackpad implements View.OnTouchListener {\n    float lastX",
    "drnasSendInput(1, drnasNativePointerX(",
]
for needle in forbidden:
    if needle in result:
        raise SystemExit(f"validation failed: superseded code still present: {needle}")

if result.count("drnasSendInput(int action, float x, float y, float value) {") != 1:
    raise SystemExit("validation failed: emitter must be defined exactly once")
if result.count("private void drnasClampLayout()") != 1:
    raise SystemExit("validation failed: layout clamp must be defined exactly once")
if result.count("class DrnasTrackpad") != 1:
    raise SystemExit("validation failed: exactly one trackpad implementation expected")

activity.write_text(result)

# ------------------------------------------------------------------ 14. ship the logic classes
# The Android-free classes are compiled into the APK next to the activity, exactly like the
# existing DrnasInputHold / DrnasZoomSpring helpers.
java_dir = activity.parent
shipped = [
    "DrnasPointerMap.java",
    "DrnasRelativePad.java",
    "DrnasMouseState.java",
    "DrnasControlLayout.java",
    "DrnasGestureEngine.java",
]
for name in shipped:
    source = patch_dir / name
    if not source.is_file():
        raise SystemExit(f"missing logic class {source}")
    (java_dir / name).write_text(source.read_text(), encoding="utf-8")
    installed = java_dir / name
    if not installed.is_file() or "package org.blender.blender;" not in installed.read_text():
        raise SystemExit(f"{name} was not installed into the APK source tree")

print("PASS: touch layer, dock MMB/wheel, navigation widget, presets, lifecycle release "
      f"({len(shipped)} logic classes installed)")
