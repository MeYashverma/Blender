package org.blender.blender;

/**
 * Independent state for the three mouse buttons the dock can drive.
 *
 * <p>Blender needs buttons that can be held <em>and</em> combined: middle-drag orbits while left
 * stays down in box-select, right-click while middle is held pans a file browser, and so on. Each
 * bit therefore has its own press/latch state; there is no single "mouse down" flag anywhere.
 *
 * <p>The class is the only place that decides whether a button may be sent. The middle button
 * needs the DRNAS native patch (the stock v7 {@code libblender.so} only knows left/right), so
 * {@link #setMiddleAvailable(boolean)} gates it and the UI hides or disables it instead of
 * pretending to work.
 *
 * <p>Android-free; covered by {@code DrnasMouseStateTest}.
 */
public final class DrnasMouseState {

  public static final int LEFT = 1;
  public static final int RIGHT = 2;
  public static final int MIDDLE = 4;

  /** Emits the real native button event. */
  public interface Sink {
    void mouseButton(int bit, boolean down);
  }

  private final Sink sink;
  private int held;
  private int latched;
  private boolean middleAvailable = true;

  public DrnasMouseState(Sink sink) {
    if (sink == null) {
      throw new IllegalArgumentException("sink");
    }
    this.sink = sink;
  }

  public void setMiddleAvailable(boolean available) {
    middleAvailable = available;
    if (!available && (held & MIDDLE) != 0) {
      // The running native library cannot release what it never received, but the overlay must
      // still not believe the button is down.
      held &= ~MIDDLE;
      latched &= ~MIDDLE;
    }
  }

  public boolean isMiddleAvailable() {
    return middleAvailable;
  }

  public boolean isAvailable(int bit) {
    return bit != MIDDLE || middleAvailable;
  }

  public int heldMask() {
    return held;
  }

  public int latchedMask() {
    return latched;
  }

  public boolean isHeld(int bit) {
    return (held & bit) != 0;
  }

  public boolean isLatched(int bit) {
    return (latched & bit) != 0;
  }

  /** Press and keep pressed; used for press-and-hold and for latching. */
  public boolean press(int bit) {
    if (!isAvailable(bit) || (held & bit) != 0) {
      return false;
    }
    held |= bit;
    sink.mouseButton(bit, true);
    return true;
  }

  public boolean release(int bit) {
    if ((held & bit) == 0) {
      return false;
    }
    held &= ~bit;
    sink.mouseButton(bit, false);
    return true;
  }

  /** Momentary click: press and release immediately, or unlock a latched button. */
  public boolean click(int bit) {
    if (!isAvailable(bit)) {
      return false;
    }
    if ((latched & bit) != 0) {
      latched &= ~bit;
      return release(bit);
    }
    if ((held & bit) != 0) {
      return release(bit);
    }
    press(bit);
    return release(bit);
  }

  /** Latch a button so it stays down until {@link #click} or {@link #releaseAll} unlocks it. */
  public boolean latch(int bit) {
    if (!isAvailable(bit)) {
      return false;
    }
    final boolean changed = press(bit);
    latched |= bit;
    return changed;
  }

  public boolean toggleLatch(int bit) {
    if (!isAvailable(bit)) {
      return false;
    }
    if ((latched & bit) != 0) {
      return click(bit);
    }
    return latch(bit);
  }

  /** Unlocks every latched button and releases every held one. */
  public void releaseAll() {
    latched = 0;
    for (int bit : new int[] {LEFT, RIGHT, MIDDLE}) {
      release(bit);
    }
  }

}
