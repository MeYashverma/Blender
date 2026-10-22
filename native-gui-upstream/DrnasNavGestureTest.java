package org.blender.blender;

import java.util.ArrayList;
import java.util.List;

/** Regression tests for orientation-puck tap, orbit, two-finger pan and cancellation. */
public final class DrnasNavGestureTest {
  private DrnasNavGestureTest() {}

  public static void main(String[] args) {
    List<String> events = new ArrayList<>();
    DrnasNavGesture nav = new DrnasNavGesture(new DrnasNavGesture.Sink() {
      @Override public void begin(boolean pan) { events.add(pan ? "pan+" : "orbit+"); }
      @Override public void move(float dx, float dy, boolean pan) {
        events.add((pan ? "pan:" : "orbit:") + dx + "," + dy);
      }
      @Override public void end(boolean pan) { events.add(pan ? "pan-" : "orbit-"); }
    }, 8f);

    nav.down(10f, 10f, 1);
    nav.move(13f, 13f, 1);
    check(nav.up(), "movement below touch slop remains a tap");
    check(events.isEmpty(), "a tap must not send mouse movement");

    nav.down(10f, 10f, 1);
    nav.move(22f, 12f, 1);
    nav.move(26f, 15f, 1);
    nav.pointerCountChanged(2);
    nav.move(28f, 18f, 2);
    nav.pointerCountChanged(1);
    nav.move(30f, 19f, 1);
    check(!nav.up(), "a dragged puck is not an axis tap");
    check(events.toString().equals(
        "[orbit+, orbit:12.0,2.0, orbit:4.0,3.0, orbit-, pan+, pan:2.0,3.0, pan-, orbit+, orbit:2.0,1.0, orbit-]"),
        "one-finger orbit and two-finger pan transitions release each native gesture cleanly: " + events);

    events.clear();
    nav.down(0f, 0f, 1);
    nav.move(10f, 0f, 1);
    nav.cancel();
    check(events.get(events.size() - 1).equals("orbit-"),
        "cancel must release an active orbit button");

    System.out.println("PASS: orientation tap/orbit/pan state, touch slop and cancel release");
  }

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
