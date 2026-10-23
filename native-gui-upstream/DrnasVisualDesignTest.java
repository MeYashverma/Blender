package org.blender.blender;

/** Offline design-system checks: icon coverage, contrast, target geometry and opacity hierarchy. */
public final class DrnasVisualDesignTest {

  private DrnasVisualDesignTest() {}

  public static void main(String[] args) {
    check("LMB", DrnasIcons.has(DrnasIcons.LMB) && DrnasIcons.fill(DrnasIcons.LMB) != null);
    check("MMB", DrnasIcons.has(DrnasIcons.MMB) && DrnasIcons.fill(DrnasIcons.MMB) != null);
    check("RMB", DrnasIcons.has(DrnasIcons.RMB) && DrnasIcons.fill(DrnasIcons.RMB) != null);
    String[] required = {"pan", "orbit", "zoom_in", "zoom_out", "move", "rotate", "scale",
        "undo", "redo", "save", "keyboard", "settings", "nav", "view_eye", "lock",
        "reset", "trash", "duplicate", "frame", "frame_all", "persp", "ortho", "view_front", "view_back",
        "view_left", "view_right", "view_top", "view_bottom", "search", "wheel_up",
        "wheel_down", "wheel_left", "wheel_right", "grip"};
    for (String icon : required) check("vector " + icon, DrnasIcons.has(icon));

    // Fail on unsupported path commands, empty glyphs, or coordinates outside the shared 24x24
    // coordinate system. DrnasChrome's parser handles all these exact command types.
    final java.util.regex.Pattern number = java.util.regex.Pattern.compile("[-+]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)");
    for (String name : DrnasIcons.names()) {
      check("stroke or fill " + name, DrnasIcons.stroke(name) != null || DrnasIcons.fill(name) != null);
      validatePath("outline " + name, DrnasIcons.stroke(name), number);
      validatePath("fill " + name, DrnasIcons.fill(name), number);
    }

    check("touch targets remain at least 48dp", DrnasSkin.TOUCH_MIN >= 48f);
    check("icon stays compact within a touch target", DrnasSkin.ICON_LG <= DrnasSkin.TOUCH_MIN / 2f);
    check("center MMB target is at least the touch minimum", DrnasSkin.DOCK_TARGET_CENTRE >= DrnasSkin.TOUCH_MIN);
    check("dock left/right touch targets remain at least 48dp", DrnasSkin.DOCK_TARGET >= DrnasSkin.TOUCH_MIN);
    check("active is fully prominent", DrnasSkin.ALPHA_ACTIVE == 1f);
    check("idle remains visibly discoverable", DrnasSkin.ALPHA_IDLE >= 0.8f);
    check("minimized remains discoverable", DrnasSkin.ALPHA_MINIMIZED >= 0.5f);
    check("fast press feedback", DrnasSkin.PRESS_MS >= 100 && DrnasSkin.PRESS_MS <= 180);
    check("fast panel animation", DrnasSkin.PANEL_MS >= 100 && DrnasSkin.PANEL_MS <= 180);
    check("consistent 4dp grid", DrnasSkin.snap(11f) == 12f && DrnasSkin.snap(14f) == 16f);
    check("inactive background is not bright blue", channel(DrnasSkin.SURFACE, 16) < 64
        && channel(DrnasSkin.SURFACE, 8) < 64 && channel(DrnasSkin.SURFACE, 0) < 64);

    for (int backdrop : new int[] {0xFF08090A, 0xFF73777C, 0xFFFAFAFA}) {
      final int surface = DrnasSkin.over(DrnasSkin.SURFACE, backdrop);
      final int foreground = DrnasSkin.over(DrnasSkin.ICON, surface);
      final float ratio = DrnasSkin.contrast(foreground, surface);
      check("icon contrast over viewport #" + Integer.toHexString(backdrop), ratio >= 4.5f);
      final int idleForeground = DrnasSkin.over(DrnasSkin.withAlphaScale(DrnasSkin.ICON,
          DrnasSkin.ALPHA_IDLE), surface);
      check("idle icon contrast over viewport #" + Integer.toHexString(backdrop),
          DrnasSkin.contrast(idleForeground, surface) >= 3.0f);
    }

    check("pressed state uses one restrained accent", DrnasSkin.ACCENT == 0xFF4772B3);
    check("disabled remains visible", DrnasSkin.ALPHA_DISABLED > 0.3f);
    check("surface has modest translucency", DrnasSkin.alphaOf(DrnasSkin.SURFACE) >= 0.85f
        && DrnasSkin.alphaOf(DrnasSkin.SURFACE) <= 0.92f);
    check("compact toolbar cells", DrnasSkin.TOOL_CELL <= 48f
        && DrnasSkin.TOOL_CELL_TOUCH >= DrnasSkin.TOUCH_MIN);

    System.out.println("PASS: Blender dark visual system, " + DrnasIcons.names().length
        + " local vector icons, contrast over light/mid/dark viewports, touch-target separation,");
    System.out.println("      active/idle/minimized hierarchy, animation timings and compact geometry");
  }

  private static void validatePath(String what, String path, java.util.regex.Pattern number) {
    if (path == null) return;
    String remaining = path.replaceAll("[MmLlHhVvCcQqZz]", "")
        .replaceAll("[-+]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)", "")
        .replaceAll("[ ,]", "");
    check(what + " uses supported syntax", remaining.isEmpty());
    java.util.regex.Matcher matcher = number.matcher(path);
    while (matcher.find()) {
      final float coordinate = Float.parseFloat(matcher.group());
      check(what + " coordinate in 24x24 viewbox", coordinate >= 0f && coordinate <= DrnasIcons.VIEWBOX);
    }
  }

  private static int channel(int color, int shift) { return (color >>> shift) & 0xFF; }

  private static void check(String what, boolean ok) {
    if (!ok) throw new AssertionError("FAIL: " + what);
  }
}
