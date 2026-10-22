package org.blender.blender;

/** Contract tests for the purpose-specific radial tool pages and their touch hit regions. */
public final class DrnasRadialPaletteModelTest {
  private DrnasRadialPaletteModelTest() {}

  public static void main(String[] args) {
    for (int page = 0; page < DrnasRadialPaletteModel.PAGE_COUNT; page++) {
      for (int slot = 0; slot < DrnasRadialPaletteModel.SLOT_COUNT; slot++) {
        DrnasRadialPaletteModel.Item item = DrnasRadialPaletteModel.itemAt(page, slot);
        check(item != null && item.id > 0 && item.label.length() > 0,
            "every purpose page has eight named actions");
        check(DrnasIcons.has(item.icon), "every radial action has a local vector icon: " + item.icon);
      }
    }
    check(DrnasRadialPaletteModel.itemAt(-1, 0) == null
        && DrnasRadialPaletteModel.itemAt(0, 8) == null, "invalid actions are inert");
    check(DrnasRadialPaletteModel.nextPage(0) == 1
        && DrnasRadialPaletteModel.nextPage(3) == 0, "the hub cycles purpose pages");
    check("Timeline".equals(DrnasRadialPaletteModel.pageName(2)), "page title is stable");

    final float cx = 100f, cy = 100f, inner = 28f, outer = 90f;
    check(DrnasRadialPaletteModel.hitSlot(cx, cy - 60, cx, cy, inner, outer) == 0,
        "north maps to slot zero");
    check(DrnasRadialPaletteModel.hitSlot(cx + 60, cy, cx, cy, inner, outer) == 2,
        "east maps clockwise to slot two");
    check(DrnasRadialPaletteModel.hitSlot(cx, cy + 60, cx, cy, inner, outer) == 4,
        "south maps to slot four");
    check(DrnasRadialPaletteModel.hitSlot(cx - 60, cy, cx, cy, inner, outer) == 6,
        "west maps to slot six");
    check(DrnasRadialPaletteModel.hitSlot(cx, cy, cx, cy, inner, outer)
        == DrnasRadialPaletteModel.CENTER, "the central hub is not an action wedge");
    check(DrnasRadialPaletteModel.hitSlot(cx + 100, cy, cx, cy, inner, outer)
        == DrnasRadialPaletteModel.OUTSIDE, "outside touches are ignored");

    System.out.println("PASS: four radial purpose pages, icon coverage, cycling and polar hit testing");
  }

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
