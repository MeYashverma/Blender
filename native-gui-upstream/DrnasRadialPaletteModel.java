package org.blender.blender;

/** Android-free command pages and polar hit testing for the contextual Blender tool wheel. */
public final class DrnasRadialPaletteModel {
  public static final int PAGE_OBJECT = 0;
  public static final int PAGE_EDIT = 1;
  public static final int PAGE_TIMELINE = 2;
  public static final int PAGE_VIEW = 3;
  public static final int PAGE_COUNT = 4;
  public static final int SLOT_COUNT = 8;
  public static final int CENTER = -1;
  public static final int OUTSIDE = -2;

  public static final int SELECT = 1;
  public static final int MOVE = 2;
  public static final int ROTATE = 3;
  public static final int SCALE = 4;
  public static final int DUPLICATE = 5;
  public static final int DELETE = 6;
  public static final int UNDO = 7;
  public static final int REDO = 8;
  public static final int EXTRUDE = 9;
  public static final int INSET = 10;
  public static final int BEVEL = 11;
  public static final int LOOP_CUT = 12;
  public static final int PLAY_PAUSE = 13;
  public static final int PREVIOUS_FRAME = 14;
  public static final int NEXT_FRAME = 15;
  public static final int INSERT_KEY = 16;
  public static final int FIRST_FRAME = 17;
  public static final int LAST_FRAME = 18;
  public static final int FRAME_SELECTED = 19;
  public static final int FRAME_ALL = 20;
  public static final int FRONT_VIEW = 21;
  public static final int RIGHT_VIEW = 22;
  public static final int TOP_VIEW = 23;
  public static final int PERSPECTIVE = 24;
  public static final int SEARCH = 25;
  public static final int HIDE = 26;
  public static final int BACK_VIEW = 27;
  public static final int LEFT_VIEW = 28;
  public static final int BOTTOM_VIEW = 29;

  public static final class Item {
    public final int id;
    public final String label;
    public final String icon;

    Item(int id, String label, String icon) {
      this.id = id;
      this.label = label;
      this.icon = icon;
    }
  }

  private static final Item[][] PAGES = {
      {
          item(SELECT, "Select", "select"), item(MOVE, "Move", "move"),
          item(ROTATE, "Rotate", "rotate"), item(SCALE, "Scale", "scale"),
          item(DUPLICATE, "Duplicate", "duplicate"), item(DELETE, "Delete", "trash"),
          item(UNDO, "Undo", "undo"), item(REDO, "Redo", "redo")
      },
      {
          item(EXTRUDE, "Extrude", "extrude"), item(LOOP_CUT, "Loop cut", "loop_cut"),
          item(BEVEL, "Bevel", "bevel"), item(INSET, "Inset", "inset"),
          item(SELECT, "Select", "select"), item(DUPLICATE, "Duplicate", "duplicate"),
          item(DELETE, "Delete", "trash"), item(UNDO, "Undo", "undo")
      },
      {
          item(PLAY_PAUSE, "Play", "play"), item(PREVIOUS_FRAME, "Prev frame", "previous"),
          item(NEXT_FRAME, "Next frame", "next"), item(INSERT_KEY, "Keyframe", "keyframe"),
          item(FIRST_FRAME, "First", "first_frame"), item(LAST_FRAME, "Last", "last_frame"),
          item(UNDO, "Undo", "undo"), item(REDO, "Redo", "redo")
      },
      {
          item(FRAME_ALL, "Frame all", "frame_all"), item(FRONT_VIEW, "Front", "view_front"),
          item(RIGHT_VIEW, "Right", "view_right"), item(TOP_VIEW, "Top", "view_top"),
          item(BACK_VIEW, "Back", "view_back"), item(LEFT_VIEW, "Left", "view_left"),
          item(BOTTOM_VIEW, "Bottom", "view_bottom"), item(PERSPECTIVE, "Persp/ortho", "ortho")
      }
  };

  private DrnasRadialPaletteModel() {}

  private static Item item(int id, String label, String icon) {
    return new Item(id, label, icon);
  }

  public static Item itemAt(int page, int slot) {
    if (page < 0 || page >= PAGE_COUNT || slot < 0 || slot >= SLOT_COUNT) return null;
    return PAGES[page][slot];
  }

  public static String pageName(int page) {
    switch (Math.floorMod(page, PAGE_COUNT)) {
      case PAGE_EDIT: return "Edit";
      case PAGE_TIMELINE: return "Timeline";
      case PAGE_VIEW: return "View";
      default: return "Object";
    }
  }

  public static int nextPage(int page) {
    return Math.floorMod(page + 1, PAGE_COUNT);
  }

  /**
   * Resolves a point relative to a circular palette. Slot zero is north and slots proceed
   * clockwise; the inner hub is the page switcher, and points outside the ring are ignored.
   */
  public static int hitSlot(float x, float y, float centerX, float centerY,
                            float innerRadius, float outerRadius) {
    if (innerRadius < 0f || outerRadius <= innerRadius) return OUTSIDE;
    final float dx = x - centerX;
    final float dy = y - centerY;
    final float distance = (float)Math.sqrt(dx * dx + dy * dy);
    if (distance < innerRadius) return CENTER;
    if (distance > outerRadius) return OUTSIDE;
    double angle = Math.atan2(dy, dx) + Math.PI * 0.5;
    if (angle < 0d) angle += Math.PI * 2d;
    return Math.min(SLOT_COUNT - 1, (int)(angle / (Math.PI * 2d / SLOT_COUNT)));
  }
}
