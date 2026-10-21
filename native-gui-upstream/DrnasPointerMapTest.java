import org.blender.blender.DrnasPointerMap;

/** Pointer mapping is a correctness requirement, so it is tested exhaustively here. */
public class DrnasPointerMapTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private static void near(float actual, float expected, float tolerance, String message) {
    if (Math.abs(actual - expected) > tolerance) {
      throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
    }
  }

  public static void main(String[] args) {
    // 1. Identity: same rectangles must not move the pointer at all.
    DrnasPointerMap map = new DrnasPointerMap();
    map.setNativeSize(1000, 600);
    map.setViewSize(1000, 600);
    check(map.isReady(), "both rectangles known");
    near(map.nativeX(123f), 123f, 0.001f, "identity x");
    near(map.nativeY(456f), 456f, 0.001f, "identity y");
    near(map.viewX(123f), 123f, 0.001f, "identity round trip");

    // 2. Stretch: a stretched window (popup) must still reach every native edge and corner.
    map.setNativeSize(2610, 1200);
    map.setViewSize(1080, 2392);
    near(map.nativeX(0f), 0f, 0.001f, "top-left corner x");
    near(map.nativeY(0f), 0f, 0.001f, "top-left corner y");
    near(map.nativeX(1079f), 2609f, 0.001f, "bottom-right corner x");
    near(map.nativeY(2391f), 1199f, 0.001f, "bottom-right corner y");
    near(map.nativeX(539.5f), 1304.5f, 0.001f, "centre x");
    // The cursor sprite must be placed back on the same point Blender was told about.
    for (float x = 0f; x <= 1079f; x += 97f) {
      near(map.viewX(map.nativeX(x)), x, 0.01f, "view->native->view is stable at " + x);
    }
    near(map.viewY(map.nativeY(2391f)), 2391f, 0.01f, "stretch round trip y");

    // 3. The reverse case: view larger than the native surface (insets/keyboard shrank GHOST).
    map.setNativeSize(1080, 800);
    map.setViewSize(2610, 2400);
    near(map.nativeX(2610f), 1079f, 0.001f, "clamped overshoot x");
    near(map.nativeY(2400f), 799f, 0.001f, "clamped overshoot y");
    near(map.nativeX(-40f), 0f, 0.001f, "negative input clamps to the first pixel");
    near(map.nativeY(-40f), 0f, 0.001f, "negative input clamps to the first pixel");

    // 4. Monotonic: no fold-back in the middle of the display.
    float previous = -1f;
    for (float x = 0f; x <= 2610f; x += 13f) {
      float native = map.nativeX(x);
      check(native >= previous, "mapping must be monotonic");
      previous = native;
    }

    // 5. Sub-rectangle mode is 1:1 plus offset and never scales.
    map.setMode(DrnasPointerMap.MODE_SUBRECT);
    map.setNativeSize(2610, 1200);
    map.setViewSize(800, 400);
    map.setViewOrigin(120, 60);
    near(map.nativeX(0f), 120f, 0.001f, "subrect origin x");
    near(map.nativeY(0f), 60f, 0.001f, "subrect origin y");
    near(map.nativeX(10f), 130f, 0.001f, "subrect is 1:1, never scaled");
    near(map.viewX(130f), 10f, 0.001f, "subrect round trip");
    near(map.nativeX(5000f), 919f, 0.001f, "subrect clamps at the view edge, not the native edge");
    near(map.viewX(0f), 0f, 0.001f, "subrect clamps to view origin");

    // 6. Degenerate sizes must not divide by zero (a window that is not measured yet).
    DrnasPointerMap fresh = new DrnasPointerMap();
    check(!fresh.isReady(), "unmeasured rectangles are not ready");
    check(fresh.nativeX(50f) == 0f && fresh.nativeY(50f) == 0f, "unmeasured map is inert");

    // 7. Rotation: swapping the native surface immediately re-scales without a second scale.
    map.setMode(DrnasPointerMap.MODE_FIT);
    map.setNativeSize(1200, 2610);
    map.setViewSize(1080, 2392);
    near(map.nativeX(1079f), 1199f, 0.001f, "rotation keeps the edges addressable");
    near(map.viewX(map.nativeX(700f)), 700f, 0.01f, "rotation round trip");

    // 8. Cursor sprite helper places the tip inside the mapped point.
    int left = map.cursorLeft(600f, 40f, 0);
    int top = map.cursorTop(600f, 40f, 0);
    check(left >= 0 && top >= 0, "cursor sprite offset stays on screen");

    System.out.println("PASS: identity, stretch, reverse stretch, clamping, monotonic, sub-rect, "
        + "degenerate sizes, rotation and sprite placement");
  }
}
