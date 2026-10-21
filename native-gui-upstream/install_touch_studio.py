#!/usr/bin/env python3
"""Install the DRNAS Touch Studio overlay into the pinned Blender Android Activity."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: install_touch_studio.py <upstream-blender-root>")

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
activity = root / "build_files/android/apk/app/src/main/java/org/blender/blender/BlenderActivity.java"
manifest = root / "build_files/android/apk/app/src/main/AndroidManifest.xml"
java_dir = activity.parent
s = activity.read_text(encoding="utf-8")

def once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"Refusing ambiguous patch for {label}: expected one anchor, found {count}")
    s = s.replace(old, new, 1)

once("  private native void nativeOnKey(int keycode, int action, int metaState);",
     "  private native void nativeOnKey(int keycode, int action, int metaState);\n"
     "  private native void nativeOnDrnasInput(int action, float x, float y, float value);",
     "native input bridge")

if "installDrnasOverlay();" in s:
    once("installDrnasOverlay();", "installDrnasTouchStudio();", "legacy overlay hook")
else:
    once("    addContentView(inputView, new ViewGroup.LayoutParams(1, 1));",
         "    addContentView(inputView, new ViewGroup.LayoutParams(1, 1));\n"
         "    installDrnasTouchStudio();", "native activity install hook")

anchor = "  private void requestAllFilesAccess() {"
snippet = (here / "drnas_touch_studio.java.inc").read_text(encoding="utf-8")
once(anchor, snippet + "\n" + anchor, "Touch Studio implementation")

if re.search(r"protected\s+void\s+onPause\s*\(", s):
    match = re.search(r"protected\s+void\s+onPause\s*\(\s*\)\s*\{", s)
    if not match:
        raise SystemExit("Found an onPause overload that needs manual review")
    body_start = match.end()
    s = s[:body_start] + "\n    drnasStudioReleaseAll();" + s[body_start:]
else:
    once(anchor, "  @Override protected void onPause() {\n"
         "    drnasStudioReleaseAll();\n"
         "    super.onPause();\n"
         "  }\n\n" + anchor, "lifecycle release")

activity.write_text(s, encoding="utf-8")
m = manifest.read_text(encoding="utf-8")
if 'package="org.blender.blender"' not in m:
    raise SystemExit("Manifest package changed; review before repacking")
m = m.replace('package="org.blender.blender"', 'package="hr.drnas.blendertouchstudio2026"', 1)
m = m.replace('android:label="Blender"', 'android:label="Blender Touch Studio"')
manifest.write_text(m, encoding="utf-8")

hold = (here / "DrnasInputHold.java").read_text(encoding="utf-8")
(java_dir / "DrnasInputHold.java").write_text(hold, encoding="utf-8")
print("PASS: installed isolated Touch Studio UI, native input bridge, lifecycle releases and hold-state helper")
