package org.blender.blender;

/**
 * Touchpad semantics for the floating Blender mouse controller.
 *
 * <p>One finger moves the cursor; a tap is LMB. Two-finger vertical/horizontal travel scrolls,
 * pinching zooms, and a two-finger tap is RMB. Orbit is deliberately explicit: hold the separate
 * MMB control while moving one finger on the pad. Pointer IDs, not transient indices, own motion.
 * Android-free so the interaction contract is unit tested.
 */
public final class DrnasMousePadGesture {
  public interface Sink {
    void pointer(float dx, float dy);
    void wheel(float vertical, float horizontal);
    void click(int button);
  }

  public static final int LEFT = 1;
  public static final int RIGHT = 2;
  public static final int MIDDLE = 4;

  public static final int ACTION_DOWN = 0;
  public static final int ACTION_MOVE = 1;
  public static final int ACTION_POINTER_DOWN = 2;
  public static final int ACTION_POINTER_UP = 3;
  public static final int ACTION_UP = 4;
  public static final int ACTION_CANCEL = 5;

  private static final int IDLE = 0;
  private static final int ONE_PENDING = 1;
  private static final int POINTER = 2;
  private static final int TWO_PENDING = 3;
  private static final int SCROLL = 4;
  private static final int PINCH = 5;
  private static final int TWO_WAIT_UP = 6;
  private static final int IGNORE = 7;
  private static final int MAX_POINTERS = 10;

  private final Sink sink;
  private final int[] ids = new int[MAX_POINTERS];
  private final float[] xs = new float[MAX_POINTERS];
  private final float[] ys = new float[MAX_POINTERS];
  private int count;
  private int state = IDLE;
  private int tapCount;
  private long downAt;
  private float startX, startY, lastX, lastY;
  private float startCx, startCy, startSpan, lastCx, lastCy, lastSpan;
  private float wheelPixels = 32f;
  private float pinchPixels = 30f;
  private float tapSlop = 10f;
  private long tapTimeoutMs = 320L;
  private float remainderY;
  private float remainderX;

  public DrnasMousePadGesture(Sink sink) {
    if (sink == null) throw new IllegalArgumentException("sink");
    this.sink = sink;
  }

  public void configure(float wheelStepPx, float pinchStepPx, float touchSlopPx, long tapTimeout) {
    wheelPixels = clamp(wheelStepPx, 8f, 160f);
    pinchPixels = clamp(pinchStepPx, 8f, 160f);
    tapSlop = clamp(touchSlopPx, 2f, 60f);
    tapTimeoutMs = Math.max(80L, Math.min(1200L, tapTimeout));
  }

  public int activePointerCount() { return count; }
  public boolean isActive() { return state != IDLE; }

  public void onTouch(int action, int[] pointerIds, float[] pointerXs, float[] pointerYs,
                      int actionIndex, long timeMs) {
    if (pointerIds == null || pointerXs == null || pointerYs == null) return;
    final int n = Math.min(MAX_POINTERS,
        Math.min(pointerIds.length, Math.min(pointerXs.length, pointerYs.length)));
    if (n <= 0) return;
    final int index = Math.max(0, Math.min(n - 1, actionIndex));

    switch (action) {
      case ACTION_DOWN:
        cancel();
        sync(pointerIds, pointerXs, pointerYs, n, -1);
        downAt = timeMs;
        tapCount = 1;
        state = ONE_PENDING;
        startX = lastX = xs[0];
        startY = lastY = ys[0];
        return;
      case ACTION_POINTER_DOWN:
        sync(pointerIds, pointerXs, pointerYs, n, -1);
        if (n == 2 && (state == ONE_PENDING || state == POINTER)) {
          final float[] c = centroid();
          startCx = lastCx = c[0];
          startCy = lastCy = c[1];
          startSpan = lastSpan = span(c[0], c[1]);
          tapCount = state == ONE_PENDING ? 2 : 0;
          state = TWO_PENDING;
        } else {
          state = IGNORE;
          tapCount = Math.max(tapCount, n);
        }
        return;
      case ACTION_MOVE:
        sync(pointerIds, pointerXs, pointerYs, n, -1);
        move(timeMs);
        return;
      case ACTION_POINTER_UP:
        sync(pointerIds, pointerXs, pointerYs, n, -1);
        if (state == TWO_PENDING && tapCount == 2 && n == 2 && !movedTwo()) {
          state = TWO_WAIT_UP;
          tapCount = 2;
        } else if (state == SCROLL || state == PINCH || state == TWO_WAIT_UP) {
          // A third pointer or a second release after a recognized multi-touch action is inert.
          state = IGNORE;
        }
        remove(pointerIds[index]);
        if (state == TWO_WAIT_UP && count == 1) {
          startX = xs[0];
          startY = ys[0];
        }
        return;
      case ACTION_UP:
        sync(pointerIds, pointerXs, pointerYs, n, -1);
        if ((state == ONE_PENDING && tapCount == 1 && !movedOne()
                || state == TWO_PENDING && tapCount == 2 && !movedTwo()
                || state == TWO_WAIT_UP)
            && timeMs >= downAt && timeMs - downAt <= tapTimeoutMs) {
          sink.click(tapCount == 2 ? RIGHT : LEFT);
        }
        reset();
        return;
      case ACTION_CANCEL:
        cancel();
        return;
      default:
        return;
    }
  }

  public void cancel() {
    reset();
    remainderX = remainderY = 0f;
  }

  private void move(long timeMs) {
    if (state == TWO_WAIT_UP) {
      if (count > 0 && (float)Math.hypot(xs[0] - startX, ys[0] - startY) > tapSlop) {
        state = IGNORE;
      }
      return;
    }
    if (state == ONE_PENDING || state == POINTER) {
      if (movedOne()) state = POINTER;
      if (state == POINTER) {
        final float dx = xs[0] - lastX;
        final float dy = ys[0] - lastY;
        if (dx != 0f || dy != 0f) sink.pointer(dx, dy);
      }
      lastX = xs[0];
      lastY = ys[0];
      return;
    }
    if (state != TWO_PENDING && state != SCROLL && state != PINCH) return;

    final float[] c = centroid();
    final float currentSpan = span(c[0], c[1]);
    final float travel = (float)Math.hypot(c[0] - startCx, c[1] - startCy);
    final float spanTravel = Math.abs(currentSpan - startSpan);
    if (state == TWO_PENDING) {
      if (spanTravel > Math.max(tapSlop, pinchPixels * 0.35f)
          && spanTravel > travel * 0.65f) {
        state = PINCH;
      } else if (travel > tapSlop) {
        state = SCROLL;
      }
    }
    if (state == SCROLL) {
      emitWheel(lastCy - c[1], c[0] - lastCx);
    } else if (state == PINCH) {
      emitWheel((currentSpan - lastSpan) * 0.8f, 0f);
    }
    lastCx = c[0];
    lastCy = c[1];
    lastSpan = currentSpan;
  }

  private void emitWheel(float dy, float dx) {
    remainderY += dy;
    remainderX += dx;
    float vertical = 0f;
    float horizontal = 0f;
    if (Math.abs(remainderY) >= wheelPixels) {
      vertical = remainderY / wheelPixels;
      remainderY = 0f;
    }
    if (Math.abs(remainderX) >= wheelPixels) {
      horizontal = remainderX / wheelPixels;
      remainderX = 0f;
    }
    if (vertical != 0f || horizontal != 0f) {
      sink.wheel(vertical, horizontal);
    }
  }

  private boolean movedOne() {
    return (float)Math.hypot(xs[0] - startX, ys[0] - startY) > tapSlop;
  }

  private boolean movedTwo() {
    final float[] c = centroid();
    return (float)Math.hypot(c[0] - startCx, c[1] - startCy) > tapSlop
        || Math.abs(span(c[0], c[1]) - startSpan) > tapSlop;
  }

  private void sync(int[] pointerIds, float[] pointerXs, float[] pointerYs, int n, int ignored) {
    for (int i = 0; i < n; i++) {
      final int found = find(pointerIds[i]);
      if (found >= 0) {
        xs[found] = pointerXs[i];
        ys[found] = pointerYs[i];
      } else if (count < MAX_POINTERS) {
        ids[count] = pointerIds[i];
        xs[count] = pointerXs[i];
        ys[count] = pointerYs[i];
        count++;
      }
    }
  }

  private float[] centroid() {
    float x = 0f, y = 0f;
    for (int i = 0; i < count; i++) { x += xs[i]; y += ys[i]; }
    return new float[] {x / Math.max(1, count), y / Math.max(1, count)};
  }

  private float span(float cx, float cy) {
    if (count < 2) return 0f;
    return (float)Math.hypot(xs[0] - cx, ys[0] - cy)
        + (float)Math.hypot(xs[1] - cx, ys[1] - cy);
  }

  private int find(int id) {
    for (int i = 0; i < count; i++) if (ids[i] == id) return i;
    return -1;
  }

  private void remove(int id) {
    final int at = find(id);
    if (at < 0) return;
    for (int i = at; i < count - 1; i++) {
      ids[i] = ids[i + 1]; xs[i] = xs[i + 1]; ys[i] = ys[i + 1];
    }
    count--;
    if (count == 0) reset();
  }

  private void reset() {
    count = 0;
    state = IDLE;
    tapCount = 0;
    downAt = 0L;
    startX = startY = lastX = lastY = 0f;
    startCx = startCy = startSpan = lastCx = lastCy = lastSpan = 0f;
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }
}
