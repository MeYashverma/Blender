package org.blender.blender;

import java.util.ArrayList;
import java.util.List;

/** Regression tests for the integrated mouse dock's momentary, latched and simultaneous buttons. */
public final class DrnasDockGestureTest {
  private DrnasDockGestureTest() {}

  public static void main(String[] args) {
    List<String> events = new ArrayList<>();
    DrnasDockGesture dock = new DrnasDockGesture(
        (bit, down) -> events.add(bit + (down ? "+" : "-")), 300L);

    dock.down(4, DrnasMouseState.LEFT, 10, true);
    dock.up(4, 50);
    check(events.toString().equals("[1+, 1-]"), "single tap is one click");

    dock.down(7, DrnasMouseState.LEFT, 180, true);
    check(dock.isLatched(DrnasMouseState.LEFT), "second tap latches");
    dock.up(7, 220);
    check(dock.isHeld(DrnasMouseState.LEFT), "latched button survives finger lift");
    dock.down(8, DrnasMouseState.LEFT, 500, true);
    dock.up(8, 520);
    check(!dock.isHeld(DrnasMouseState.LEFT) && !dock.isLatched(DrnasMouseState.LEFT),
        "next tap unlocks");
    check(events.toString().equals("[1+, 1-, 1+, 1-]"), "latch and unlock each emit once");

    events.clear();
    dock.down(21, DrnasMouseState.LEFT, 800, false);
    dock.down(22, DrnasMouseState.LEFT, 810, false);
    dock.up(21, 820);
    check(dock.isHeld(DrnasMouseState.LEFT), "one finger cannot release a zone still held by another");
    dock.up(22, 830);
    check(!dock.isHeld(DrnasMouseState.LEFT), "last owner releases a shared button zone");

    events.clear();
    dock.down(1, DrnasMouseState.LEFT, 1200, true);
    dock.down(2, DrnasMouseState.MIDDLE, 1210, false);
    dock.down(3, DrnasMouseState.RIGHT, 1220, false);
    check(dock.heldMask() == (DrnasMouseState.LEFT | DrnasMouseState.MIDDLE | DrnasMouseState.RIGHT),
        "simultaneous separate zones hold independently");
    dock.up(2, 1230);
    check(dock.isHeld(DrnasMouseState.LEFT) && dock.isHeld(DrnasMouseState.RIGHT),
        "lifting MMB does not release adjacent buttons");
    dock.cancel();
    check(dock.heldMask() == 0 && events.toString().contains("4-"),
        "cancel releases all momentary buttons");

    events.clear();
    dock.down(9, DrnasMouseState.RIGHT, 2000, true);
    dock.up(9, 2030);
    dock.down(10, DrnasMouseState.RIGHT, 2200, true);
    dock.up(10, 2250);
    dock.cancel();
    check(dock.isLatched(DrnasMouseState.RIGHT), "cancel preserves an intentional latch");
    dock.releaseAll();
    check(dock.heldMask() == 0 && dock.latchedMask() == 0, "lifecycle release clears all state");

    System.out.println("PASS: integrated dock click/latch/unlock, simultaneous zones, cancel and lifecycle release");
  }

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }
}
