package org.blender.blender;

/**
 * Geometry, persistence and preset rules for the floating control groups.
 *
 * <p>Every group (mouse dock, shortcut/keyboard strip, navigation widget) owns an independent
 * offset, size, opacity, visibility and expanded flag. This class is the single place where those
 * numbers are validated, migrated and clamped, so:
 * <ul>
 *   <li>a saved position can never leave a group unreachable off-screen – at least
 *       {@link #MIN_VISIBLE_DP} dp of it always stays inside the safe area after a rotation, a
 *       display resize, a cutout or system-gesture inset change;</li>
 *   <li>invalid or older saved values are migrated instead of resetting the user's layout;</li>
 *   <li>presets only touch the fields they mean to, and never fight with a later manual drag.</li>
 * </ul>
 *
 * <p>Android-free; covered by {@code DrnasControlLayoutTest}.
 */
public final class DrnasControlLayout {

  public static final int GROUP_MOUSE = 0;
  public static final int GROUP_KEYS = 1;
  public static final int GROUP_NAV = 2;
  public static final int GROUP_TOOLS = 3;

  public static final int PRESET_TWO_THUMB = 0;
  public static final int PRESET_ONE_HAND = 1;
  public static final int PRESET_COMPACT = 2;
  public static final int PRESET_LARGE_TARGETS = 3;
  public static final int PRESET_TABLET = 4;
  public static final int PRESET_DESKTOP = 5;

  /** How much of a group must remain on screen, in dp, so it can always be grabbed again. */
  public static final int MIN_VISIBLE_DP = 56;

  /** One floating group. Mutable on purpose: it mirrors SharedPreferences one to one. */
  public static final class Group {
    public float offsetX;
    public float offsetY;
    public int widthPercent = 100;
    public int heightPercent = 100;
    public int buttonPercent = 100;
    public int opacityPercent;
    public int rows = 2;
    public boolean visible = true;
    public boolean expanded = true;

    public Group copy() {
      Group copy = new Group();
      copy.offsetX = offsetX;
      copy.offsetY = offsetY;
      copy.widthPercent = widthPercent;
      copy.heightPercent = heightPercent;
      copy.buttonPercent = buttonPercent;
      copy.opacityPercent = opacityPercent;
      copy.rows = rows;
      copy.visible = visible;
      copy.expanded = expanded;
      return copy;
    }

    /** Stable, compact encoding for SharedPreferences (one string per group). */
    public String encode() {
      return "1;" + offsetX + ";" + offsetY + ";" + widthPercent + ";" + heightPercent + ";"
          + buttonPercent + ";" + opacityPercent + ";" + rows + ";" + (visible ? 1 : 0) + ";"
          + (expanded ? 1 : 0);
    }

    /**
     * Decodes a saved group. Anything unreadable falls back to the supplied defaults field by
     * field, so a single corrupted value never discards the rest of the layout.
     */
    public static Group decode(String encoded, Group fallback) {
      Group group = fallback == null ? new Group() : fallback.copy();
      if (encoded == null) {
        return group;
      }
      String[] parts = encoded.split(";", -1);
      if (parts.length < 10 || !"1".equals(parts[0])) {
        return group;
      }
      group.offsetX = parseFloat(parts[1], group.offsetX);
      group.offsetY = parseFloat(parts[2], group.offsetY);
      group.widthPercent = clampPercent(parseInt(parts[3], group.widthPercent), 40, 160);
      group.heightPercent = clampPercent(parseInt(parts[4], group.heightPercent), 60, 220);
      group.buttonPercent = clampPercent(parseInt(parts[5], group.buttonPercent), 60, 150);
      group.opacityPercent = clampPercent(parseInt(parts[6], group.opacityPercent), 0, 85);
      group.rows = clampPercent(parseInt(parts[7], group.rows), 1, 5);
      group.visible = !"0".equals(parts[8]);
      group.expanded = !"0".equals(parts[9]);
      return group;
    }

    public void normalise() {
      offsetX = sanitise(offsetX);
      offsetY = sanitise(offsetY);
      widthPercent = clampPercent(widthPercent, 40, 160);
      heightPercent = clampPercent(heightPercent, 60, 220);
      buttonPercent = clampPercent(buttonPercent, 60, 150);
      opacityPercent = clampPercent(opacityPercent, 0, 85);
      rows = clampPercent(rows, 1, 5);
    }
  }

  /** Screen rectangle and safe-area insets, all in px. */
  public static final class Bounds {
    public int width = 1;
    public int height = 1;
    public int insetLeft;
    public int insetTop;
    public int insetRight;
    public int insetBottom;
    public int density = 1;

    public Bounds() {
    }

    public Bounds(int width, int height, int density) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.density = Math.max(1, density);
    }

    int minVisible() {
      return Math.max(24, density * MIN_VISIBLE_DP);
    }
  }

  private DrnasControlLayout() {
  }

  /**
   * Clamps a desired offset so the group stays reachable.
   *
   * @param desired    offset the user dragged to (or a stale saved value)
   * @param base       un-offset position of the group inside the usable area
   * @param size       group size in px along this axis
   * @param extent     usable extent in px (window size minus insets on this axis)
   * @param minVisible px of the group that must stay inside
   */
  public static float clampOffset(float desired, float base, float size, float extent,
                                  float minVisible) {
    final float visible = Math.min(minVisible, Math.max(1f, size));
    // Left/top limit: at least `visible` px of the group must be past the start edge.
    final float lower = -(size - visible) - base;
    // Right/bottom limit: at least `visible` px must stay before the end edge.
    final float upper = extent - visible - base;
    if (upper < lower) {
      // The group is bigger than the screen: keep its start edge on screen.
      return Math.max(-base, Math.min(desired, extent - visible - base));
    }
    return Math.max(lower, Math.min(upper, desired));
  }


  /**
   * Clamps one axis of a panel that is positioned by an offset from its natural place, honouring
   * the safe area. The activity used to pass the raw window extent here, which let a restored or
   * dragged panel settle under the status bar, a cutout or the navigation bar even though the
   * insets were known. Both axes go through this so the overlay obeys the same contract as
   * {@link #clampGroup}.
   *
   * @param desired    offset the user dragged to (or a stale saved value)
   * @param base       natural position along this axis, in window coordinates
   * @param size       panel size in px along this axis
   * @param bounds     current window bounds including insets
   * @param horizontal true for x, false for y (insets are vertical on Android)
   */
  public static float clampInBounds(float desired, float base, float size, Bounds bounds,
                                    boolean horizontal) {
    if (bounds == null) {
      return sanitise(desired);
    }
    final float startInset = horizontal ? bounds.insetLeft : bounds.insetTop;
    final float endInset = horizontal ? bounds.insetRight : bounds.insetBottom;
    final float extent = Math.max(1f, (horizontal ? bounds.width : bounds.height)
                                          - startInset - endInset);
    final float visible = Math.min(bounds.minVisible(), Math.max(1f, size));
    return clampOffset(sanitise(desired), base - startInset, size, extent, visible);
  }

  /** Clamps both axes against the current bounds, e.g. after rotating the device. */
  public static void clampGroup(Group group, float baseX, float baseY, float width, float height,
                                Bounds bounds) {
    if (group == null || bounds == null) {
      return;
    }
    final Bounds usable = new Bounds(
        Math.max(1, bounds.width - bounds.insetLeft - bounds.insetRight),
        Math.max(1, bounds.height - bounds.insetTop - bounds.insetBottom),
        bounds.density);
    final float extentX = usable.width;
    final float extentY = usable.height;
    final float visible = usable.minVisible();
    final float localBaseX = baseX - bounds.insetLeft;
    final float localBaseY = baseY - bounds.insetTop;
    group.offsetX = clampOffset(sanitise(group.offsetX), localBaseX, width, extentX, visible);
    group.offsetY = clampOffset(sanitise(group.offsetY), localBaseY, height, extentY, visible);
  }

  /** True when the group is at least partly inside the safe area (used by the editor preview). */
  public static boolean isReachable(Group group, float baseX, float baseY, float width,
                                    float height, Bounds bounds) {
    if (group == null || bounds == null) {
      return false;
    }
    final float x = baseX + group.offsetX;
    final float y = baseY + group.offsetY;
    final float visible = Math.max(1, bounds.density * MIN_VISIBLE_DP);
    return x + width > bounds.insetLeft && x < bounds.width - bounds.insetRight - visible * 0.5f
        && y + height > bounds.insetTop && y < bounds.height - bounds.insetBottom
        - visible * 0.5f;
  }

  /**
   * Applies a preset. Each group is written independently, so switching presets never has to
   * mirror controls by hand; hand-made changes after a preset simply become the new state.
   */
  public static void applyPreset(int preset, Group mouse, Group keys, Group nav) {
    applyPreset(preset, mouse, keys, nav, new Group());
  }

  /** Applies the same device layout to the movable radial tool palette as the other controls. */
  public static void applyPreset(int preset, Group mouse, Group keys, Group nav, Group tools) {
    if (mouse == null || keys == null || nav == null || tools == null) {
      return;
    }
    mouse.visible = true;
    keys.visible = true;
    nav.visible = true;
    tools.visible = true;
    mouse.expanded = true;
    keys.expanded = true;
    nav.expanded = false;
    tools.expanded = true;

    switch (preset) {
      case PRESET_ONE_HAND:
        // Everything reachable by a right thumb, nothing on the left edge.
        mouse.offsetX = 0f;
        mouse.offsetY = 0f;
        keys.offsetX = 0f;
        keys.offsetY = 0f;
        nav.offsetX = 0f;
        nav.offsetY = 0f;
        mouse.widthPercent = 100;
        keys.widthPercent = 70;
        tools.widthPercent = 85;
        tools.buttonPercent = 100;
        tools.offsetX = 0f;
        tools.offsetY = 0f;
        mouse.opacityPercent = 10;
        tools.opacityPercent = 10;
        break;
      case PRESET_COMPACT:
        mouse.buttonPercent = 70;
        keys.buttonPercent = 70;
        mouse.widthPercent = 80;
        keys.widthPercent = 60;
        keys.heightPercent = 80;
        tools.buttonPercent = 80;
        tools.widthPercent = 80;
        mouse.opacityPercent = 35;
        tools.opacityPercent = 35;
        keys.opacityPercent = 35;
        keys.expanded = false;
        break;
      case PRESET_LARGE_TARGETS:
        mouse.buttonPercent = 135;
        keys.buttonPercent = 135;
        mouse.widthPercent = 120;
        keys.widthPercent = 100;
        keys.rows = 3;
        tools.buttonPercent = 135;
        tools.widthPercent = 120;
        mouse.opacityPercent = 0;
        tools.opacityPercent = 0;
        keys.opacityPercent = 0;
        break;
      case PRESET_TABLET:
        mouse.offsetX = 0f;
        mouse.offsetY = 0f;
        mouse.buttonPercent = 110;
        keys.buttonPercent = 110;
        mouse.widthPercent = 110;
        keys.widthPercent = 100;
        keys.rows = 2;
        tools.buttonPercent = 110;
        tools.widthPercent = 110;
        mouse.opacityPercent = 20;
        tools.opacityPercent = 20;
        keys.opacityPercent = 20;
        nav.visible = true;
        nav.expanded = true;
        break;
      case PRESET_DESKTOP:
        // Physical mouse and keyboard: get the overlay out of the way, keep it restorable.
        mouse.visible = false;
        keys.visible = false;
        nav.visible = false;
        tools.visible = false;
        break;
      case PRESET_TWO_THUMB:
      default:
        mouse.offsetX = 0f;
        mouse.offsetY = 0f;
        keys.offsetX = 0f;
        keys.offsetY = 0f;
        nav.offsetX = 0f;
        nav.offsetY = 0f;
        mouse.buttonPercent = 100;
        keys.buttonPercent = 100;
        tools.buttonPercent = 100;
        tools.widthPercent = 100;
        keys.rows = 2;
        mouse.opacityPercent = 0;
        keys.opacityPercent = 0;
        tools.opacityPercent = 0;
        break;
    }
    mouse.normalise();
    keys.normalise();
    nav.normalise();
    tools.normalise();
  }

  public static String presetName(int preset) {
    switch (preset) {
      case PRESET_ONE_HAND:
        return "Phone - one hand (right)";
      case PRESET_COMPACT:
        return "Compact overlay";
      case PRESET_LARGE_TARGETS:
        return "Large touch targets";
      case PRESET_TABLET:
        return "Tablet";
      case PRESET_DESKTOP:
        return "External mouse + keyboard";
      case PRESET_TWO_THUMB:
      default:
        return "Phone - two thumbs";
    }
  }

  public static int clampPercent(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  /** NaNs and infinities come from broken JSON/prefs; they must never reach a layout pass. */
  static float sanitise(float value) {
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      return 0f;
    }
    return Math.max(-100000f, Math.min(100000f, value));
  }

  private static float parseFloat(String text, float fallback) {
    try {
      return sanitise(Float.parseFloat(text.trim()));
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private static int parseInt(String text, int fallback) {
    try {
      return Integer.parseInt(text.trim());
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }
}
