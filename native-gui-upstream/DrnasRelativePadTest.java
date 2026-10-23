import org.blender.blender.DrnasRelativePad;

public class DrnasRelativePadTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private static void near(float actual, float expected, float tolerance, String message) {
    if (Math.abs(actual - expected) > tolerance) {
      throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
    }
  }

  public static void main(String[] args) {
    // 1. A slow, precise swipe must move exactly by speed * distance.
    DrnasRelativePad pad = new DrnasRelativePad();
    pad.setBounds(1079f, 2391f);
    pad.setAcceleration(0f);
    pad.setSpeed(1f);
    pad.setPosition(500f, 500f);
    check(pad.move(10f, 0f), "a real swipe moves the pointer");
    near(pad.x(), 510f, 1e-4f, "no acceleration means 1:1 movement");

    // 2. Sub-pixel travel accumulates instead of being truncated away (precision).
    pad.setSpeed(0.25f);
    pad.setPosition(100f, 100f);
    for (int i = 0; i < 8; i++) pad.move(1f, 0f);
    near(pad.x(), 102f, 1e-4f, "eight 0.25px steps must add up to 2px");

    // 3. Acceleration raises gain for fast swipes but leaves slow ones alone.
    pad.setSpeed(1f);
    pad.setAcceleration(0.5f);
    pad.setAccelerationSpan(20f);
    near(pad.gain(1f), 1.025f, 0.001f, "slow swipe is almost unaccelerated");
    near(pad.gain(20f), 1.5f, 1e-4f, "fast swipe gains the configured maximum");
    near(pad.gain(400f), 1.5f, 1e-4f, "acceleration saturates, never explodes");

    // 4. A fast swipe must cross the whole display, and the pointer must reach every edge.
    pad.setPosition(540f, 1200f);
    pad.move(-10000f, -10000f);
    near(pad.x(), 0f, 1e-4f, "top-left corner is reachable");
    near(pad.y(), 0f, 1e-4f, "top-left corner is reachable");
    check(!pad.move(-100f, -100f), "already clamped at the corner");
    pad.move(10000f, 10000f);
    near(pad.x(), 1079f, 1e-4f, "last addressable pixel column is reachable");
    near(pad.y(), 2391f, 1e-4f, "last addressable pixel row is reachable");

    // 5. Direction is preserved (no axis inversion, no absolute snapping).
    pad.setPosition(500f, 500f);
    pad.move(-40f, 25f);
    check(pad.x() < 500f && pad.y() > 500f, "both axes follow the finger direction");

    // 6. Bounds changes (rotation, keyboard inset) keep the pointer inside and re-centre on demand.
    pad.setBounds(2391f, 1079f);
    check(pad.x() <= 2391f && pad.y() <= 1079f, "rotation clamps into the new surface");
    pad.centre();
    near(pad.x(), 2391f / 2f, 1e-4f, "centre after rotation");
    near(pad.y(), 1079f / 2f, 1e-4f, "centre after rotation");

    // 7. Speed is bounded so a bad preference cannot make the pad unusable or dead.
    pad.setSpeed(99f);
    near(pad.speed(), 3f, 1e-4f, "speed upper bound");
    pad.setSpeed(0f);
    near(pad.speed(), 0.25f, 1e-4f, "speed lower bound keeps slow precision usable");
    pad.setAcceleration(-5f);
    near(pad.acceleration(), 0f, 1e-4f, "acceleration cannot go negative");

    // 8. Zero movement is not a change, so no redundant native events are produced.
    pad.setAcceleration(0.35f);
    pad.setSpeed(1f);
    pad.setPosition(10f, 10f);
    check(!pad.move(0f, 0f), "no delta, no event");

    System.out.println("PASS: precision, sub-pixel accumulation, acceleration curve, edge reach, "
        + "direction, rotation bounds, speed limits and idle suppression");
  }
}
