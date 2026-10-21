#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
activity = root / "build_files/android/apk/app/src/main/java/org/blender/blender/BlenderActivity.java"
manifest = root / "build_files/android/apk/app/src/main/AndroidManifest.xml"
s = activity.read_text(encoding="utf-8")
m = manifest.read_text(encoding="utf-8")
for token in (
    "installDrnasTouchStudio();",
    "nativeOnDrnasInput(int action, float x, float y, float value)",
    "drnasStudioBuildMouseGroup()",
    "drnasStudioBuildToolGroup()",
    "drnasStudioBuildViewGroup()",
    "drnasStudioShowSettings()",
    "drnasStudioReleaseAll()",
    "EDIT LAYOUT",
    "GESTURE SENSITIVITY %",
):
    assert token in s, f"missing injected feature: {token}"
assert 'package="hr.drnas.blendertouchstudio2026"' in m
assert s.count("installDrnasTouchStudio();") == 1
print("PASS: Touch Studio static integration checks")
