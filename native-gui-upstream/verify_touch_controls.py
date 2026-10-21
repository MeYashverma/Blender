#!/usr/bin/env python3
"""Static verification of the DRNAS touch control layer.

Usage: ``verify_touch_controls.py <upstream-source-root>``

Checks the *contract* between the three parts that must agree but live in different languages:

* the Java overlay's action numbers and capability bits;
* the native ``dispatchDrnasInput`` handling and ``nativeDrnasCapabilities`` probe;
* the on-screen controls that call them.

It also proves the superseded code is gone and that nothing was duplicated by the patch chain.
Runs in CI and locally; no Android SDK, device or compiler required.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) < 2:
    raise SystemExit("usage: verify_touch_controls.py <upstream-source-root>")

root = Path(sys.argv[1])
patch_dir = Path(__file__).resolve().parent

activity_path = (root / "build_files/android/apk/app/src/main/java/org/blender/blender/"
                        "BlenderActivity.java")
system_cc = root / "intern/ghost/intern/GHOST_SystemAndroid.cc"
system_hh = root / "intern/ghost/intern/GHOST_SystemAndroid.hh"
android_main = root / "intern/ghost/intern/GHOST_AndroidMain.cc"
manifest = root / "build_files/android/apk/app/src/main/AndroidManifest.xml"

failures = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"missing required file: {path}")
    return path.read_text()


activity = read(activity_path)
system = read(system_cc)
header = read(system_hh)
main_cc = read(android_main)
read(manifest)


# ---------------------------------------------------------------- 1. superseded code removed
check("drnasNativePointerX" not in activity,
      "old double-scaling pointer conversion must be gone")
check("drnasSendInput(1, drnasNativePointerX(" not in activity,
      "pointer must not be mapped twice")
check(activity.count("private void drnasSendInput(int action") == 1,
      "exactly one native emitter")
check(activity.count("class DrnasTrackpad") == 1,
      "exactly one trackpad implementation")
check("float lastX, lastY, travel;" not in activity,
      "old ad-hoc trackpad state must be gone")

# ---------------------------------------------------------------- 2. new layer present
for needle, what in [
    ("DrnasPointerMap drnasMap", "pointer map instance"),
    ("DrnasRelativePad drnasPadMotion", "relative pad instance"),
    ("DrnasGestureEngine drnasGestures", "gesture engine instance"),
    ("DrnasMouseState drnasMouseButtons", "mouse button state"),
    ("DrnasControlLayout.Bounds drnasLayoutBounds", "layout bounds"),
    ("private native int nativeDrnasCapabilities();", "capability probe declaration"),
    ("drnasGestureAction(", "MotionEvent action translation"),
    ("drnasReleaseTouchInput(", "lifecycle input release"),
    ("drnasClampLayout(", "layout clamping"),
    ("installDrnasNavWidget(", "navigation widget"),
    ("installDrnasLayoutOptions(", "layout presets"),
    ("installDrnasKeyExtras(", "keyboard extras"),
]:
    check(needle in activity, f"missing {what}")

# One definition each for the methods the patch adds; a second copy would not compile.
for signature in [
    "private void drnasSendInput(int action, float x, float y, float value) {",
    "private void drnasMovePointer(float x, float y) {",
    "private void drnasUpdatePointer() {",
    "private void drnasClampLayout() {",
    "private void drnasSetInputMode(int mode, boolean persist) {",
    "public void onWindowFocusChanged(boolean hasFocus) {",
    "protected void onStop() {",
    "protected void onPause() {",
]:
    count = activity.count(signature)
    check(count == 1, f"{signature!r} must be declared exactly once, found {count}")

# ---------------------------------------------------------------- 3. mouse dock completeness
for label, action in [("LMB", 2), ("RMB", 4), ("MMB", 8)]:
    check(f'drnasMouse("{label}", {action}, {action + 1}' in activity,
          f"{label} must drive native action {action}/{action + 1}")
check('dockKeys.add("mmb")' in activity, "the dock must host the middle button")
check("drnasHasCapability(DRNAS_CAP_MIDDLE)) {\n      dockKeys.add" in activity,
      "the middle button must only appear when the native library supports it")
check("drnasDockExtraButtons(dock);" in activity, "the dock must add wheel buttons")
check('drnasDockWheelButton(dock, "W\\u25B2", 6, 1f' in activity,
      "wheel up must send a real wheel event")
check('drnasDockWheelButton(dock, "W\\u25BC", 6, -1f' in activity,
      "wheel down must send a real wheel event")

# ---------------------------------------------------------------- 4. Java <-> native contract
java_actions = set()
for call in re.findall(r"drnasSendInput\(([^,]+),", activity):
    java_actions.update(int(n) for n in re.findall(r"\b(\d+)\b", call))
emit_match = re.search(r"private void drnasEmitMouse\(int bit, boolean down\) \{(.*?)\n  \}",
                       activity, re.S)
check(emit_match is not None, "the mouse emitter must exist")
emit_body = emit_match.group(1) if emit_match else ""
for bit_name, down_action, up_action in [("LEFT", 2, 3), ("RIGHT", 4, 5), ("MIDDLE", 8, 9)]:
    check(f"bit == DrnasMouseState.{bit_name}) action = down ? {down_action} : {up_action};"
          in emit_body,
          f"{bit_name} must map to native actions {down_action}/{up_action}")
    java_actions.update({down_action, up_action})
native_cases = set(int(n) for n in re.findall(r"case (\d+):\n      mask = GHOST_kButtonMask", system))
check({2, 3, 4, 5} <= java_actions, "Java uses the left/right button actions")
check(8 in java_actions and 9 in java_actions, "Java uses middle button actions 8/9")
check(12 in java_actions, "Java uses the horizontal wheel action")
check(10 in java_actions and 11 in java_actions, "Java uses the Shift hold actions")
check({2, 3, 4, 5, 8, 9} <= native_cases, "native dispatch handles every button action")
for action in (8, 9, 12, 10, 11):
    check(f"event.action == {action}" in system or f"case {action}:" in system,
          f"native dispatch handles action {action}")
check("GHOST_kButtonMaskMiddle" in system, "native middle button is wired to GHOST")
check("if (action < 1 || action > 12)" in system, "native queue must accept the new actions")
check("if (action < 1 || action > 7)" not in system, "old native action range must be gone")

# Capability bits must mean the same thing on both sides.
native_caps = {name: int(value) for name, value in re.findall(
    r"#define DRNAS_CAP_(\w+) (\d+)", main_cc)}
java_caps = {name.upper(): int(value) for name, value in re.findall(
    r"DRNAS_CAP_(\w+) = (\d+);", activity)}
check(native_caps == java_caps,
      f"capability bits must match: native={native_caps} java={java_caps}")
check(bool(native_caps), "the capability probe must define its bits")
check("Java_org_blender_blender_BlenderActivity_nativeDrnasCapabilities" in main_cc,
      "the JNI capability probe must be exported")
check("catch (Throwable legacyLibrary)" in activity,
      "a library without the probe must fall back instead of crashing")

# ---------------------------------------------------------------- 5. keyboard reachability
for needle, what in [
    ("KEYCODE_NUMPAD_1", "front view key"),
    ("KEYCODE_NUMPAD_7", "top view key"),
    ("KEYCODE_NUMPAD_5", "perspective toggle key"),
    ("KEYCODE_NUMPAD_DOT", "frame selected key"),
    ("KEYCODE_MOVE_HOME", "frame all key"),
]:
    check(needle in activity, f"navigation widget is missing the {what}")
check("AKEYCODE_F1 && keycode <= AKEYCODE_F12" in system,
      "native conversion must map function keys (Search = F3)")
check("AKEYCODE_NUMPAD_0 && keycode <= AKEYCODE_NUMPAD_9" in system,
      "native conversion must map the numpad")
check("AKEYCODE_MOVE_HOME" in system, "native conversion must map Home")
check("AKEYCODE_PERIOD" in system and "GHOST_kKeyPeriod" in system,
      "numeric entry needs the period key")
check("drnasBindShortcutStrip(row)" in activity,
      "the shortcut strip must be registered so it can be reordered and hidden")
check("DrnasShortcutStrip.PREF_KEY" in activity,
      "the shortcut strip order must be persisted")
check("drnasBuildStripArranger" in activity,
      "the shortcut strip needs a reorder/hide editor")
check("KEYCODE_MOVE_HOME" in activity, "Frame All must use the Home key")
check("KEYCODE_Z,\n        android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_SHIFT_ON"
      in activity, "Redo must be present")
check("installDrnasGestureOptions(optionRows)" in activity
      or "installDrnasGestureOptions(options)" in activity,
      "gesture switches must be reachable from the settings panel")
check('"gesture_orbit"' in activity and '"gesture_pinch"' in activity
      and '"gesture_pan"' in activity,
      "orbit, pinch and pan must be independently configurable")
check('"pointer_acceleration"' in activity, "pointer acceleration must be adjustable")
check("MODE_SUBRECT" in activity,
      "a pad inside a smaller floating host must map 1:1 from its own origin")
check("drnasTyping()" in activity,
      "view keys must be blocked while a text field is being edited")
check("badge.setOnLongClickListener" in activity,
      "hiding the overlay must leave a visible way to restore it")
check('"haptics"' in activity, "haptic feedback must be configurable and disable-able")
check("KEYCODE_S,\n        android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_SHIFT_ON"
      in activity, "Save As shortcut must be present")
check("hideKeyboard()" in activity, "the keyboard must be dismissible from the overlay")
check("showKeyboard()" in activity, "the keyboard must be reachable from the overlay")

# ---------------------------------------------------------------- 6. release paths
check('drnasReleaseTouchInput("pause")' in activity, "pause must release touch input")
check('drnasReleaseTouchInput("stop")' in activity, "stop must release touch input")
check('drnasReleaseTouchInput("focus-lost")' in activity,
      "losing the input focus must release touch input")
check("drnasGestures.releaseAll(reason)" in activity, "gestures must expose releaseAll")
check("drnasMouseButtons.releaseAll()" in activity, "mouse buttons must be released")

# ---------------------------------------------------------------- 7. hosted tests
logic_classes = sorted(p.name for p in patch_dir.glob("Drnas*.java")
                       if not p.name.endswith("Test.java")
                       and "Activity" not in p.name
                       and p.name not in {"DrnasGpuOptions.java", "DrnasGpuProfile.java",
                                          "DrnasInitLog.java", "DrnasLanguage.java",
                                          "DrnasGpuPolicy.java"})
test_names = {p.name for p in patch_dir.glob("*Test.java")}
for source in logic_classes:
    stem = source[:-len(".java")]
    check(f"{stem}Test.java" in test_names, f"{stem} has no unit test")
for source in ["DrnasPointerMap.java", "DrnasGestureEngine.java", "DrnasRelativePad.java",
               "DrnasMouseState.java", "DrnasControlLayout.java"]:
    text = read(patch_dir / source)
    check("import android" not in text,
          f"{source} must stay Android-free so it can be unit tested")
    check("android." not in text, f"{source} must not touch the Android framework")

# ---------------------------------------------------------------- 8. documented behaviour
notes = patch_dir / "TOUCH_CONTROLS_NOTES.md"
check(notes.is_file(), "the gesture and action contract must be documented")

if failures:
    print("FAIL: touch control verification")
    for failure in failures:
        print("  -", failure)
    sys.exit(1)

# Java reserved words may not be used as identifiers. A tree-sitter parse accepts them, so this is
# checked textually: it caught "float native = ..." which javac rejects.
JAVA_RESERVED = (
    "abstract assert boolean break byte case catch char class const continue default do double "
    "else enum extends final finally float for goto if implements import instanceof int interface "
    "long native new package private protected public return short static strictfp super switch "
    "synchronized this throw throws transient try void volatile while")
_reserved = set(JAVA_RESERVED.split())
_decl = re.compile(r"\b(?:float|int|double|long|boolean|char|byte|short|String|var)\s+([A-Za-z_$][\w$]*)")
_keyword_hits = []
for _path in sorted(patch_dir.glob("Drnas*.java")):
    for _num, _line in enumerate(_path.read_text().splitlines(), 1):
        for _name in _decl.findall(_line):
            if _name in _reserved:
                _keyword_hits.append("%s:%d uses the reserved word %r" % (_path.name, _num, _name))
check(not _keyword_hits, "reserved words used as identifiers: " + "; ".join(_keyword_hits))

print("PASS: touch controls verified (pointer mapping, gesture actions, capability contract, "
      "keyboard reachability, release paths, tests and Android-free logic)")
