package org.blender.blender;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The icon set, as vector path data on a 24 x 24 grid.
 *
 * <p>No emoji, no bitmap assets, no icon dependency: each glyph is a handful of SVG-style path
 * commands that {@link DrnasIconPath} turns into an {@code android.graphics.Path} at whatever size
 * the surface needs. That keeps the APK small, keeps the icons crisp at every density, and makes
 * them auditable in a unit test (every command parses, every coordinate stays inside the grid).
 *
 * <p>{@link #STROKE} paths are drawn with {@link DrnasSkin#ICON_STROKE}; {@link #FILL} paths, where
 * present, are painted solid underneath to make the state of a control readable at a glance (the
 * filled button on the mouse glyphs, for example).
 *
 * <p>Android-free on purpose: tests and the offline preview renderer read the same data.
 */
public final class DrnasIcons {

  private DrnasIcons() {
  }

  public static final float VIEWBOX = 24f;

  /** Mouse body with the left button filled: LMB. */
  public static final String LMB = "mouse_left";
  public static final String MMB = "mouse_middle";
  public static final String RMB = "mouse_right";

  private static final Map<String, String> STROKE = new LinkedHashMap<>();
  private static final Map<String, String> FILL = new LinkedHashMap<>();

  private static void stroke(String name, String path) {
    STROKE.put(name, path);
  }

  private static void filled(String name, String fill) {
    FILL.put(name, fill);
  }

  static {
    final String mouseBody =
        "M12 3.2 C8.8 3.2 7.1 5.7 7.1 9 L7.1 16.4 C7.1 19.5 8.9 20.8 12 20.8 "
            + "C15.1 20.8 16.9 19.5 16.9 16.4 L16.9 9 C16.9 5.7 15.2 3.2 12 3.2 Z";
    stroke("mouse", mouseBody + " M12 3.2 L12 10.1");
    stroke(LMB, mouseBody + " M12 10.1 L12 3.2");
    stroke(MMB, mouseBody + " M12 10.1 L12 3.2");
    stroke(RMB, mouseBody + " M12 10.1 L12 3.2");
    filled(LMB, "M11.1 3.3 C8.7 3.7 7.1 6.1 7.1 9 L7.1 9.9 L11.1 9.9 Z");
    filled(MMB, "M11.1 3.1 L12.9 3.1 L12.9 9.9 L11.1 9.9 Z");
    filled(RMB, "M12.9 3.3 C15.3 3.7 16.9 6.1 16.9 9 L16.9 9.9 L12.9 9.9 Z");

    stroke("wheel_up", "M12 18.2 L12 6.2 M8.4 9.8 L12 6.2 L15.6 9.8");
    stroke("wheel_down", "M12 5.8 L12 17.8 M8.4 14.2 L12 17.8 L15.6 14.2");
    stroke("wheel_left", "M18.2 12 L6.2 12 M9.8 8.4 L6.2 12 L9.8 15.6");
    stroke("wheel_right", "M5.8 12 L17.8 12 M14.2 8.4 L17.8 12 L14.2 15.6");

    stroke("orbit",
        "M20.2 12 C20.2 7.5 16.5 3.8 12 3.8 C7.5 3.8 3.8 7.5 3.8 12 "
            + "C3.8 16.5 7.5 20.2 12 20.2 M3.8 12 L2.2 8.6 M3.8 12 L7.4 11");
    filled("orbit_dot", "M10.9 12 C10.9 11.4 11.4 10.9 12 10.9 C12.6 10.9 13.1 11.4 13.1 12 "
        + "C13.1 12.6 12.6 13.1 12 13.1 C11.4 13.1 10.9 12.6 10.9 12 Z");

    stroke("pan", "M4 12 L20 12 M7 8.8 L3.8 12 L7 15.2 M17 8.8 L20.2 12 L17 15.2");
    stroke("move",
        "M12 4.2 L12 19.8 M4.2 12 L19.8 12 "
            + "M9.4 6.6 L12 4 L14.6 6.6 M9.4 17.4 L12 20 L14.6 17.4 "
            + "M6.6 9.4 L4 12 L6.6 14.6 M17.4 9.4 L20 12 L17.4 14.6");

    stroke("zoom_in",
        "M10.8 4.6 C7.4 4.6 4.6 7.4 4.6 10.8 C4.6 14.2 7.4 17 10.8 17 "
            + "C14.2 17 17 14.2 17 10.8 C17 7.4 14.2 4.6 10.8 4.6 Z "
            + "M15.2 15.2 L20 20 M8.4 10.8 L13.2 10.8 M10.8 8.4 L10.8 13.2");
    stroke("zoom_out",
        "M10.8 4.6 C7.4 4.6 4.6 7.4 4.6 10.8 C4.6 14.2 7.4 17 10.8 17 "
            + "C14.2 17 17 14.2 17 10.8 C17 7.4 14.2 4.6 10.8 4.6 Z "
            + "M15.2 15.2 L20 20 M8.4 10.8 L13.2 10.8");

    stroke("rotate",
        "M19.4 12 C19.4 7.9 16.1 4.6 12 4.6 C8.6 4.6 5.7 6.8 4.7 9.9 "
            + "M4.4 5.4 L4.8 10.1 L9.5 9.3 M12 9.6 L12 14.4 M9.6 12 L14.4 12");
    stroke("scale",
        "M5.4 12.4 L5.4 18.6 L11.6 18.6 M18.6 11.6 L18.6 5.4 L12.4 5.4 "
            + "M8.4 15.6 L15.6 8.4 M15.6 8.4 L11.9 8.4 M15.6 8.4 L15.6 12.1");

    stroke("undo", "M4.6 10.4 L9.2 5.8 M4.6 10.4 L9.2 15 M4.6 10.4 L14.6 10.4 "
        + "C17.9 10.4 20.4 12.9 20.4 16.2");
    stroke("redo", "M19.4 10.4 L14.8 5.8 M19.4 10.4 L14.8 15 M19.4 10.4 L9.4 10.4 "
        + "C6.1 10.4 3.6 12.9 3.6 16.2");

    stroke("save",
        "M5.2 5.4 L16.4 5.4 L18.8 7.8 L18.8 18.6 L5.2 18.6 Z "
            + "M8.4 5.4 L8.4 10.2 L15 10.2 L15 5.4 M8.4 18.6 L8.4 13.2 L15.4 13.2 L15.4 18.6");

    stroke("keyboard",
        "M3.4 8.2 L20.6 8.2 L20.6 15.8 L3.4 15.8 Z M6.2 11 L7.6 11 M10.2 11 L11.6 11 "
            + "M14.2 11 L15.6 11 M8 13.6 L16 13.6");
    stroke("keyboard_hide",
        "M3.4 7.4 L20.6 7.4 L20.6 14 L3.4 14 Z M6.2 9.8 L7.4 9.8 M10.2 9.8 L11.4 9.8 "
            + "M14.2 9.8 L15.4 9.8 M7.6 12.2 L16.4 12.2 M9.4 21 L12 18.4 L14.6 21");

    stroke("settings",
        "M12 8.6 C10.1 8.6 8.6 10.1 8.6 12 C8.6 13.9 10.1 15.4 12 15.4 "
            + "C13.9 15.4 15.4 13.9 15.4 12 C15.4 10.1 13.9 8.6 12 8.6 Z "
            + "M12 3.2 L12 5.8 M12 18.2 L12 20.8 M3.2 12 L5.8 12 M18.2 12 L20.8 12 "
            + "M5.8 5.8 L7.6 7.6 M16.4 16.4 L18.2 18.2 M18.2 5.8 L16.4 7.6 M7.6 16.4 L5.8 18.2");

    stroke("nav",
        "M12 3.6 C7.4 3.6 3.6 7.4 3.6 12 C3.6 16.6 7.4 20.4 12 20.4 "
            + "C16.6 20.4 20.4 16.6 20.4 12 C20.4 7.4 16.6 3.6 12 3.6 Z "
            + "M15.2 8.8 L12.9 12.9 L8.8 15.2 L11.1 11.1 Z");

    stroke("view_eye",
        "M2.8 12 C5.1 8.2 8.4 6.3 12 6.3 C15.6 6.3 18.9 8.2 21.2 12 "
            + "C18.9 15.8 15.6 17.7 12 17.7 C8.4 17.7 5.1 15.8 2.8 12 Z "
            + "M12 9.7 C10.7 9.7 9.7 10.7 9.7 12 C9.7 13.3 10.7 14.3 12 14.3 "
            + "C13.3 14.3 14.3 13.3 14.3 12 C14.3 10.7 13.3 9.7 12 9.7 Z");

    stroke("lock",
        "M7.6 10.8 L7.6 8.6 C7.6 6.2 9.6 4.3 12 4.3 C14.4 4.3 16.4 6.2 16.4 8.6 L16.4 10.8 "
            + "M5.9 10.8 L18.1 10.8 L18.1 19.6 L5.9 19.6 Z M12 13.6 L12 16.8");
    stroke("reset",
        "M19.6 12 C19.6 7.8 16.2 4.4 12 4.4 C8.7 4.4 5.9 6.4 4.7 9.3 "
            + "M4.4 5.2 L4.8 9.9 L9.5 9.1 M12 8.9 L12 15.1 M8.9 12 L15.1 12");

    stroke("trash",
        "M5.4 7.2 L18.6 7.2 M9.4 7.2 L9.4 5.2 L14.6 5.2 L14.6 7.2 "
            + "M6.9 7.2 L7.9 19.6 L16.1 19.6 L17.1 7.2 M10.1 10.4 L10.1 17 M13.9 10.4 L13.9 17");
    stroke("duplicate",
        "M8 8 L18.6 8 L18.6 18.6 L8 18.6 Z M5.4 15.6 L5.4 5.4 L15.6 5.4");

    stroke("tab",
        "M16.4 6.4 L3.8 6.4 L3.8 15.6 L11 15.6 M13.4 12 L20.4 12 M17.4 8.6 L20.8 12 L17.4 15.4");
    stroke("search",
        "M10.6 4.4 C7.2 4.4 4.4 7.2 4.4 10.6 C4.4 14 7.2 16.8 10.6 16.8 "
            + "C14 16.8 16.8 14 16.8 10.6 C16.8 7.2 14 4.4 10.6 4.4 Z "
            + "M15 15 L20 20");

    stroke("plus", "M12 5 L12 19 M5 12 L19 12");
    stroke("minus", "M5 12 L19 12");
    stroke("check", "M5 12.6 L9.6 17.2 L19 6.6");
    stroke("close", "M7 7 L17 17 M17 7 L7 17");
    stroke("chevron_down", "M5.6 9.4 L12 15.8 L18.4 9.4");
    stroke("chevron_up", "M5.6 14.6 L12 8.2 L18.4 14.6");
    stroke("chevron_left", "M14.6 5.6 L8.2 12 L14.6 18.4");
    stroke("chevron_right", "M9.4 5.6 L15.8 12 L9.4 18.4");
    stroke("grip", "M8.6 7.5 L15.4 7.5 M8.6 12 L15.4 12 M8.6 16.5 L15.4 16.5");

    stroke("frame",
        "M4.6 9 L4.6 4.6 L9 4.6 M15 4.6 L19.4 4.6 L19.4 9 "
            + "M19.4 15 L19.4 19.4 L15 19.4 M9 19.4 L4.6 19.4 L4.6 15 "
            + "M9.2 9.2 L14.8 9.2 L14.8 14.8 L9.2 14.8 Z");
    stroke("persp", "M12 3.6 L20 8 L20 16 L12 20.4 L4 16 L4 8 Z "
        + "M4 8 L12 12.4 L20 8 M12 12.4 L12 20.4");
    stroke("ortho", "M4.6 7.4 L19.4 7.4 L19.4 16.6 L4.6 16.6 Z M8.4 12 L15.6 12");
    stroke("view_front", "M5 6 L19 6 L19 18 L5 18 Z M9 10 L15 10 L15 14 L9 14 Z");
    stroke("view_back", "M6 6 L18 6 L18 18 L6 18 Z M9 9 L15 9 M9 12 L15 12 M9 15 L15 15");
    stroke("view_left", "M18.5 6 L10 6 L5 12 L10 18 L18.5 18 M5 12 L14 12");
    stroke("view_right", "M5.5 6 L14 6 L19 12 L14 18 L5.5 18 M19 12 L10 12");
    stroke("view_top", "M12 4.5 L19.5 9 L12 13.5 L4.5 9 Z M12 13.5 L12 19.5");
    stroke("view_bottom", "M12 10.5 L19.5 15 L12 19.5 L4.5 15 Z M12 4.5 L12 10.5");
    stroke("frame_all", "M3.8 8.6 L3.8 3.8 L8.6 3.8 M15.4 3.8 L20.2 3.8 L20.2 8.6 "
        + "M20.2 15.4 L20.2 20.2 L15.4 20.2 M8.6 20.2 L3.8 20.2 L3.8 15.4 "
        + "M8 12 L16 12 M12 8 L12 16");
  }

  /** Names of every icon, in a stable order (used by the tests and the preview renderer). */
  public static String[] names() {
    final java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(STROKE.keySet());
    all.addAll(FILL.keySet());
    return all.toArray(new String[0]);
  }

  public static boolean has(String name) {
    return name != null && (STROKE.containsKey(name) || FILL.containsKey(name));
  }

  /** Outline path, or {@code null} when the icon is fill-only. */
  public static String stroke(String name) {
    return name == null ? null : STROKE.get(name);
  }

  /** Solid underlay path, or {@code null} when the icon is stroke-only. */
  public static String fill(String name) {
    return name == null ? null : FILL.get(name);
  }
}
