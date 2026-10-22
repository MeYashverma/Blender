package org.blender.blender;

import java.util.HashMap;
import java.util.Map;

/** Android-free state machine behind the integrated three-zone mouse dock. */
public final class DrnasDockGesture {
  public interface Sink {
    void button(int bit, boolean down);
  }

  private final Sink sink;
  private final Map<Integer, Integer> pointers = new HashMap<>();
  private final long[] lastTap = {-1L, -1L, -1L};
  private final int[] owners = new int[3];
  private int held;
  private int latched;
  private long doubleTapMs;

  public DrnasDockGesture(Sink sink, long doubleTapMs) {
    if (sink == null || doubleTapMs < 0L) throw new IllegalArgumentException();
    this.sink = sink;
    this.doubleTapMs = doubleTapMs;
  }

  public void setDoubleTapMs(long value) {
    if (value < 0L) throw new IllegalArgumentException("doubleTapMs");
    doubleTapMs = value;
  }

  /** Begin one finger on a button zone; bits are DrnasMouseState LEFT/MIDDLE/RIGHT. */
  public boolean down(int pointerId, int bit, long timeMs, boolean eligibleForDoubleTap) {
    if (!valid(bit) || pointers.containsKey(pointerId)) return false;
    if ((latched & bit) != 0) {
      latched &= ~bit;
      owners[index(bit)] = 0;
      release(bit);
      pointers.put(pointerId, 0);
      lastTap[index(bit)] = -1L;
      return true;
    }
    final int i = index(bit);
    final long previous = lastTap[i];
    final boolean latch = eligibleForDoubleTap && previous >= 0L
        && timeMs >= previous && timeMs - previous <= doubleTapMs;
    if (owners[i]++ == 0) press(bit);
    pointers.put(pointerId, bit);
    if (latch) latched |= bit;
    lastTap[i] = -1L;
    return true;
  }

  public boolean up(int pointerId, long timeMs) {
    final Integer boxed = pointers.remove(pointerId);
    if (boxed == null) return false;
    final int bit = boxed;
    if (bit != 0) {
      final int i = index(bit);
      if (owners[i] > 0) owners[i]--;
      if (owners[i] == 0 && (latched & bit) == 0) {
        release(bit);
        lastTap[i] = timeMs;
      }
    }
    return true;
  }

  /** ACTION_CANCEL releases momentary owners but keeps intentionally latched buttons. */
  public void cancel() {
    for (int bit : new int[] {DrnasMouseState.LEFT, DrnasMouseState.MIDDLE,
        DrnasMouseState.RIGHT}) {
      if ((held & bit) != 0 && (latched & bit) == 0) release(bit);
      lastTap[index(bit)] = -1L;
    }
    pointers.clear();
    owners[0] = owners[1] = owners[2] = 0;
  }

  public void releaseAll() {
    latched = 0;
    owners[0] = owners[1] = owners[2] = 0;
    for (int bit : new int[] {DrnasMouseState.LEFT, DrnasMouseState.MIDDLE,
        DrnasMouseState.RIGHT}) release(bit);
    pointers.clear();
    lastTap[0] = lastTap[1] = lastTap[2] = -1L;
  }

  public int heldMask() { return held; }
  public int latchedMask() { return latched; }
  public boolean isHeld(int bit) { return (held & bit) != 0; }
  public boolean isLatched(int bit) { return (latched & bit) != 0; }

  private void press(int bit) {
    if ((held & bit) != 0) return;
    held |= bit;
    sink.button(bit, true);
  }

  private void release(int bit) {
    if ((held & bit) == 0) return;
    held &= ~bit;
    sink.button(bit, false);
  }

  private static boolean valid(int bit) {
    return bit == DrnasMouseState.LEFT || bit == DrnasMouseState.MIDDLE
        || bit == DrnasMouseState.RIGHT;
  }

  private static int index(int bit) {
    return bit == DrnasMouseState.LEFT ? 0 : bit == DrnasMouseState.MIDDLE ? 1 : 2;
  }
}
