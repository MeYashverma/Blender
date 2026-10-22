import org.blender.blender.DrnasControlLayout;
import org.blender.blender.DrnasControlLayout.Bounds;
import org.blender.blender.DrnasControlLayout.Group;

public class DrnasControlLayoutTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private static void near(float actual, float expected, float tolerance, String message) {
    if (Math.abs(actual - expected) > tolerance) {
      throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
    }
  }

  public static void main(String[] args) {
    // 1. Save/restore round trip keeps every field.
    Group mouse = new Group();
    mouse.offsetX = -42.5f;
    mouse.offsetY = 17f;
    mouse.widthPercent = 120;
    mouse.heightPercent = 80;
    mouse.buttonPercent = 130;
    mouse.opacityPercent = 25;
    mouse.rows = 3;
    mouse.visible = false;
    mouse.expanded = false;
    Group restored = Group.decode(mouse.encode(), null);
    check(restored.offsetX == -42.5f && restored.offsetY == 17f, "offsets survive");
    check(restored.widthPercent == 120 && restored.heightPercent == 80, "sizes survive");
    check(restored.buttonPercent == 130 && restored.rows == 3, "button size and rows survive");
    check(restored.opacityPercent == 25, "opacity survives");
    check(!restored.visible && !restored.expanded, "visibility and expansion survive");

    // 2. Missing or corrupt data migrates to defaults instead of resetting by throwing.
    check(Group.decode(null, null).widthPercent == 100, "null decodes to defaults");
    Group fallback = new Group();
    fallback.opacityPercent = 40;
    check(Group.decode(null, fallback).opacityPercent == 40, "null keeps the supplied defaults");
    check(Group.decode("garbage", fallback).opacityPercent == 40, "garbage keeps the defaults");
    check(Group.decode("", fallback).opacityPercent == 40, "empty keeps the defaults");
    // A single bad field must not discard the readable ones.
    Group partial = Group.decode("1;oops;5;120;90;100;10;2;1;0", fallback);
    check(partial.offsetX == 0f, "unreadable offset falls back");
    check(partial.offsetY == 5f, "readable offset is kept");
    check(partial.widthPercent == 120, "readable width is kept");
    check(!partial.expanded, "expansion flag is kept");

    // 3. Out-of-range saved values are normalised, never trusted.
    Group wild = Group.decode("1;0;0;9999;-4;500;900;42;1;1", null);
    check(wild.widthPercent == 160 && wild.heightPercent == 60, "percent bounds applied");
    check(wild.buttonPercent == 150, "button percent bounded");
    check(wild.opacityPercent == 85 && wild.rows == 5, "opacity and rows bounded");

    // 4. A stale off-screen offset is pulled back so the group stays grabbable.
    Bounds bounds = new Bounds(1000, 2000, 2);
    near(DrnasControlLayout.clampOffset(5000f, 100f, 200f, 1000f, 112f), 788f, 0.001f,
        "dragged far right is clamped with 112px still visible");
    near(DrnasControlLayout.clampOffset(-5000f, 100f, 200f, 1000f, 112f), -188f, 0.001f,
        "dragged far left is clamped with 112px still visible");
    near(DrnasControlLayout.clampOffset(10f, 100f, 200f, 1000f, 112f), 10f, 0.001f,
        "an on-screen offset is untouched");

    // 5. Rotation/resize correction: the saved layout survives and is reachable afterwards.
    Group keys = new Group();
    keys.offsetX = 5000f;
    keys.offsetY = -5000f;
    DrnasControlLayout.clampGroup(keys, 100f, 100f, 200f, 100f, bounds);
    check(keys.offsetX <= 788f && keys.offsetY >= -100f, "clamped into the new bounds");
    check(DrnasControlLayout.isReachable(keys, 100f, 100f, 200f, 100f, bounds),
        "clamped group is reachable again");
    // Landscape: much wider, much shorter.
    Bounds landscape = new Bounds(2400, 1080, 3);
    DrnasControlLayout.clampGroup(keys, 1200f, 900f, 400f, 150f, landscape);
    check(DrnasControlLayout.isReachable(keys, 1200f, 900f, 400f, 150f, landscape),
        "layout is corrected after a rotation");

    // 6. Insets (cutout, system bars) are honoured, not ignored.
    Bounds inset = new Bounds(1000, 2000, 2);
    inset.insetLeft = 80;
    inset.insetTop = 60;
    inset.insetBottom = 40;
    Group nav = new Group();
    nav.offsetY = 100000f;
    DrnasControlLayout.clampGroup(nav, 0f, 1800f, 120f, 120f, inset);
    check(nav.offsetY <= 48.001f, "a group cannot be pushed entirely under the navigation bar");
    check(DrnasControlLayout.isReachable(nav, 0f, 1800f, 120f, 120f, inset),
        "and it stays reachable above the bar");

    // 7. NaN/infinite saved values are sanitised before a layout pass.
    Group broken = new Group();
    broken.offsetX = Float.NaN;
    broken.offsetY = Float.POSITIVE_INFINITY;
    broken.normalise();
    check(broken.offsetX == 0f && broken.offsetY == 0f, "non-finite offsets are zeroed");

    // 8. Presets set every group independently and stay inside valid ranges.
    Group pMouse = new Group();
    Group pKeys = new Group();
    Group pNav = new Group();
    DrnasControlLayout.applyPreset(DrnasControlLayout.PRESET_ONE_HAND, pMouse, pKeys, pNav);
    check(pKeys.widthPercent == 70, "one-hand preset narrows the key strip");
    check(pMouse.visible && pKeys.visible && pNav.visible, "one-hand keeps every group usable");
    DrnasControlLayout.applyPreset(DrnasControlLayout.PRESET_COMPACT, pMouse, pKeys, pNav);
    check(pMouse.opacityPercent == 35 && !pKeys.expanded, "compact preset is translucent and folded");
    check(pMouse.buttonPercent == 70, "compact preset shrinks the buttons");
    DrnasControlLayout.applyPreset(DrnasControlLayout.PRESET_LARGE_TARGETS, pMouse, pKeys, pNav);
    check(pMouse.buttonPercent == 135 && pKeys.rows == 3, "large-target preset enlarges everything");
    DrnasControlLayout.applyPreset(DrnasControlLayout.PRESET_DESKTOP, pMouse, pKeys, pNav);
    check(!pMouse.visible && !pKeys.visible && !pNav.visible,
        "desktop preset hides the overlay without destroying its layout");
    DrnasControlLayout.applyPreset(DrnasControlLayout.PRESET_TWO_THUMB, pMouse, pKeys, pNav);
    check(pMouse.visible && pKeys.visible, "switching back restores the overlay");
    check(pMouse.buttonPercent == 100 && pKeys.rows == 2, "two-thumb preset restores defaults");
    for (Group group : new Group[] {pMouse, pKeys, pNav}) {
      check(group.widthPercent >= 40 && group.widthPercent <= 160
          && group.heightPercent >= 60 && group.heightPercent <= 220
          && group.buttonPercent >= 60 && group.buttonPercent <= 150
          && group.opacityPercent >= 0 && group.opacityPercent <= 85,
          "preset output stays within the supported ranges");
    }

    // 9. Preset names are user visible and stable.
    check("Tablet".equals(DrnasControlLayout.presetName(DrnasControlLayout.PRESET_TABLET)),
        "preset names are available to the editor");


    // 10. Safe-area clamping. The activity used to clamp panels against the raw window size, so a
    // restored or dragged panel could settle under the status bar, a cutout or the nav bar.
    Bounds safe = new Bounds(1080, 2400, 3);
    safe.insetTop = 90;
    safe.insetBottom = 140;
    safe.insetRight = 60;
    // The contract is "MIN_VISIBLE_DP stays reachable", measured against the *inset-adjusted*
    // extent. The old code measured it against the full window, which let a restored panel park
    // itself entirely under a system bar.
    float visible = Math.min(Math.max(24f, 3f * DrnasControlLayout.MIN_VISIBLE_DP), 200f);
    float bottom = DrnasControlLayout.clampInBounds(100000f, 100f, 200f, safe, false);
    check(bottom + 100f + visible <= 2400f - 140f + 0.01f,
        "at least a thumb's worth of a panel stays clear of the navigation bar");
    float top = DrnasControlLayout.clampInBounds(-100000f, 400f, 200f, safe, false);
    check(top + 400f + 200f >= 90f + visible - 0.01f,
        "the panel cannot be dragged above the status bar");
    float right = DrnasControlLayout.clampInBounds(100000f, 100f, 200f, safe, true);
    check(right + 100f + visible <= 1080f - 60f + 0.01f, "the cutout inset is respected");
    Bounds plain = new Bounds(1080, 2400, 3);
    near(DrnasControlLayout.clampInBounds(4000f, 100f, 200f, plain, false),
        DrnasControlLayout.clampOffset(4000f, 100f, 200f, 2400f, Math.min(56 * 3f, 200f)),
        0.01f, "without insets the helper matches the plain clamp");
    near(DrnasControlLayout.clampInBounds(Float.NaN, 100f, 200f, safe, false),
        DrnasControlLayout.clampInBounds(0f, 100f, 200f, safe, false), 0.01f,
        "a NaN offset is sanitised, not propagated");

    System.out.println("PASS: persistence round trip, migration, corruption tolerance, clamping, "
        + "rotation correction, safe-area insets, sanitising and presets");
  }
}
