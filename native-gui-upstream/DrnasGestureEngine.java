package org.blender.blender;

/**
 * Android-free touch gesture recogniser for the DRNAS overlay.
 *
 * <p>The engine mirrors Android MotionEvent action/pointer-id semantics but imports nothing from
 * Android, so every transition can be unit tested off-device. It turns finger input
 * into the <em>mouse</em> input Blender actually understands:
 *
 * <table border="1">
 *   <caption>Default gesture model</caption>
 *   <tr><th>Gesture</th><th>Emitted input</th></tr>
 *   <tr><td>One-finger tap</td><td>LMB click (press and release at the landing point)</td></tr>
 *   <tr><td>One-finger drag</td><td>LMB down at the landing point, pointer moves, LMB up</td></tr>
 *   <tr><td>Two-finger drag</td><td>MMB down, pointer follows the centroid, MMB up (orbit)</td></tr>
 *   <tr><td>Pinch in/out</td><td>wheel notches (zoom)</td></tr>
 *   <tr><td>Three-finger drag</td><td>Shift + MMB down (pan in the viewport)</td></tr>
 *   <tr><td>Two-finger tap</td><td>RMB click (context menu)</td></tr>
 * </table>
 *
 * <p>Guarantees relied upon by the overlay and covered by {@code DrnasGestureEngineTest}:
 * <ul>
 *   <li>a press is emitted at the point where the finger landed, never where it has already
 *       travelled to;</li>
 *   <li>every press has exactly one matching release, including after a cancel or a lifecycle
 *       stop ({@link #releaseAll(String)});</li>
 *   <li>a gesture never also produces a click;</li>
 *   <li>pointer <em>ids</em>, not indices, identify fingers, so reordering fingers mid-gesture
 *       cannot move the pointer or swap buttons;</li>
 *   <li>sub-pixel wheel remainders are accumulated, not dropped.</li>
 * </ul>
 */
public final class DrnasGestureEngine {

  public static final int BUTTON_LEFT = 1;
  public static final int BUTTON_RIGHT = 2;
  public static final int BUTTON_MIDDLE = 3;

  public static final int WHEEL_VERTICAL = 0;
  public static final int WHEEL_HORIZONTAL = 1;

  public static final int MODIFIER_SHIFT = 1;

  public static final int GESTURE_ORBIT = 0;
  public static final int GESTURE_PAN = 1;
  public static final int GESTURE_ZOOM = 2;

  /** MotionEvent-compatible actions. */
  public static final int ACTION_DOWN = 0;
  public static final int ACTION_MOVE = 1;
  public static final int ACTION_POINTER_DOWN = 2;
  public static final int ACTION_POINTER_UP = 3;
  public static final int ACTION_UP = 4;
  public static final int ACTION_CANCEL = 5;

  private static final int STATE_IDLE = 0;
  /** One finger down, not yet a drag. */
  private static final int STATE_PENDING = 1;
  private static final int STATE_DRAG = 2;
  /** Two fingers down, orbit vs zoom not decided yet. */
  private static final int STATE_TWO_UNDECIDED = 3;
  private static final int STATE_ORBIT = 4;
  private static final int STATE_ZOOM = 5;
  private static final int STATE_PAN = 6;
  /** A gesture just ended but a finger is still on the glass: never turn that into a click. */
  private static final int STATE_IGNORE = 7;

  /** Receives the semantic result. All coordinates are in view space. */
  public interface Sink {
    void pointer(float x, float y, float dx, float dy);

    void button(int button, boolean down);

    void wheel(int axis, float amount);

    void modifier(int modifier, boolean down);

    void gesture(int kind, boolean active);
  }

  private static final int MAX_POINTERS = 10;

  private final Sink sink;

  private final int[] ids = new int[MAX_POINTERS];
  private final float[] xs = new float[MAX_POINTERS];
  private final float[] ys = new float[MAX_POINTERS];
  private int count;

  private int state = STATE_IDLE;
  private long downAt;
  private float originX, originY;
  private float lastX, lastY;
  private float startCentroidX, startCentroidY, startSpread;
  private float prevCentroidX, prevCentroidY, prevSpread;
  private float zoomRemainder;
  private boolean leftDown, middleDown, shiftDown;
  /** Pointer id that started the current left-drag; -1 when no drag is running. */
  private int dragPointerId = -1;
  private boolean gestureClassified;
  private boolean scrollFallback;
  private boolean secondTapPending;
  private int gestureKind = -1;

  /* Configuration, all clamped by the setters. */
  private float dragSlop = 12f;
  private float decideSlop = 18f;
  private long tapTimeoutMs = 320;
  private float zoomNotchesPerPixel = 0.02f;
  private boolean twoFingerOrbit = true;
  private boolean pinchZoomEnabled = true;
  private boolean threeFingerPanEnabled = true;
  private boolean twoFingerTapRightClick = true;

  public DrnasGestureEngine(Sink sink) {
    if (sink == null) {
      throw new IllegalArgumentException("sink");
    }
    this.sink = sink;
  }

  /* ------------------------------------------------------------------ configuration */

  /** Travel, in px, before a one-finger press becomes a drag instead of a click. */
  public void setDragSlop(float px) {
    dragSlop = Math.max(2f, Math.min(80f, px));
  }

  /** Travel, in px, before two fingers are classified as orbit or zoom. */
  public void setDecideSlop(float px) {
    decideSlop = Math.max(4f, Math.min(120f, px));
  }

  public void setTapTimeout(long ms) {
    tapTimeoutMs = Math.max(80L, Math.min(1200L, ms));
  }

  public void setZoomNotchesPerPixel(float notches) {
    zoomNotchesPerPixel = Math.max(0.002f, Math.min(0.2f, notches));
  }

  public void setTwoFingerOrbit(boolean enabled) {
    twoFingerOrbit = enabled;
  }

  public void setPinchZoom(boolean enabled) {
    pinchZoomEnabled = enabled;
  }

  public void setThreeFingerPan(boolean enabled) {
    threeFingerPanEnabled = enabled;
  }

  public void setTwoFingerTapRightClick(boolean enabled) {
    twoFingerTapRightClick = enabled;
  }

  public boolean isLeftDown() {
    return leftDown;
  }

  public boolean isMiddleDown() {
    return middleDown;
  }

  public boolean isShiftDown() {
    return shiftDown;
  }

  public int activePointerCount() {
    return count;
  }

  /** -1 when no navigation gesture is running, else {@link #GESTURE_ORBIT} etc. */
  public int activeGesture() {
    return gestureKind;
  }

  /* ------------------------------------------------------------------ input */

  /**
   * Feeds one pointer batch, exactly like {@code onTouchEvent}.
   *
   * @param action      one of the {@code ACTION_*} constants
   * @param pointerIds  ids of every pointer in this batch (for POINTER_UP/UP this still includes
   *                    the pointer that is leaving, as Android reports it)
   * @param pointerXs   x of every pointer, view space
   * @param pointerYs   y of every pointer, view space
   * @param actionIndex index of the pointer the action talks about
   * @param timeMs      event time
   */
  public void onTouch(int action,
                      int[] pointerIds,
                      float[] pointerXs,
                      float[] pointerYs,
                      int actionIndex,
                      long timeMs) {
    if (pointerIds == null || pointerXs == null || pointerYs == null) {
      return;
    }
    final int n = Math.min(MAX_POINTERS,
                           Math.min(pointerIds.length, Math.min(pointerXs.length, pointerYs.length)));
    if (n == 0) {
      return;
    }
    final int index = Math.max(0, Math.min(n - 1, actionIndex));

    switch (action) {
      case ACTION_DOWN:
      case ACTION_POINTER_DOWN:
        store(pointerIds, pointerXs, pointerYs, n);
        down(index, timeMs);
        break;
      case ACTION_MOVE:
        store(pointerIds, pointerXs, pointerYs, n);
        move(timeMs);
        break;
      case ACTION_POINTER_UP:
        store(pointerIds, pointerXs, pointerYs, n);
        pointerUp(index, timeMs);
        break;
      case ACTION_UP:
        store(pointerIds, pointerXs, pointerYs, n);
        lastX = pointerXs[index];
        lastY = pointerYs[index];
        up(timeMs);
        break;
      case ACTION_CANCEL:
        releaseAll("cancel");
        break;
      default:
        break;
    }
  }

  private void store(int[] pointerIds, float[] pointerXs, float[] pointerYs, int n) {
    count = n;
    for (int i = 0; i < n; i++) {
      ids[i] = pointerIds[i];
      xs[i] = pointerXs[i];
      ys[i] = pointerYs[i];
    }
  }

  private int indexOfId(int id) {
    for (int i = 0; i < count; i++) {
      if (ids[i] == id) {
        return i;
      }
    }
    return -1;
  }

  private void down(int index, long timeMs) {
    if (count == 1) {
      // Fresh gesture.
      if (state != STATE_IDLE) {
        releaseAll("restart");
      }
      state = STATE_PENDING;
      downAt = timeMs;
      originX = xs[0];
      originY = ys[0];
      lastX = originX;
      lastY = originY;
      zoomRemainder = 0f;
      gestureClassified = false;
      secondTapPending = false;
      sink.pointer(originX, originY, 0f, 0f);
      return;
    }

    if (count == 2) {
      if (leftDown) {
        // A drag is already in progress: extra fingers must not steal it, or the object being
        // moved would be dropped mid-transform. The gesture resumes when every finger is up.
        state = STATE_IGNORE;
        return;
      }
      if (state == STATE_PENDING) {
        // A gesture is never also a click: drop the pending press.
        state = STATE_TWO_UNDECIDED;
      } else if (state == STATE_IDLE || state == STATE_IGNORE) {
        state = STATE_TWO_UNDECIDED;
      } else {
        return;
      }
      computeCentroid();
      startCentroidX = prevCentroidX;
      startCentroidY = prevCentroidY;
      startSpread = prevSpread;
      gestureClassified = false;
      gestureKind = -1;
      zoomRemainder = 0f;
      secondTapPending = false;
      downAt = timeMs;
      return;
    }

    // Three or more fingers: pan, if the drag is not busy.
    if (count >= 3) {
      if (leftDown) {
        state = STATE_IGNORE;
        return;
      }
      endNavigationGesture();
      if (threeFingerPanEnabled) {
        computeCentroid();
        startCentroidX = prevCentroidX;
        startCentroidY = prevCentroidY;
        state = STATE_PAN;
        gestureClassified = true;
        gestureKind = GESTURE_PAN;
        sink.gesture(GESTURE_PAN, true);
        // Shift must precede the middle button: a keymap item is matched against the modifiers
        // already down when the press arrives.
        shiftDown = true;
        sink.modifier(MODIFIER_SHIFT, true);
        middleDown = true;
        sink.button(BUTTON_MIDDLE, true);
        lastX = prevCentroidX;
        lastY = prevCentroidY;
        sink.pointer(prevCentroidX, prevCentroidY, 0f, 0f);
      } else {
        state = STATE_IGNORE;
      }
    }
  }

  private void move(long timeMs) {
    if (count <= 0) {
      return;
    }

    if (count == 1) {
      final float x = xs[0];
      final float y = ys[0];
      if (state == STATE_PENDING) {
        final float travel = (float) Math.hypot(x - originX, y - originY);
        if (travel > dragSlop) {
          // Press where the finger landed; Blender measures its own drag threshold from there.
          sink.pointer(originX, originY, originX - lastX, originY - lastY);
          lastX = originX;
          lastY = originY;
          leftDown = true;
          dragPointerId = ids[0];
          state = STATE_DRAG;
          sink.button(BUTTON_LEFT, true);
        }
      }
      if (state == STATE_DRAG || state == STATE_PENDING) {
        sink.pointer(x, y, x - lastX, y - lastY);
        lastX = x;
        lastY = y;
      }
      return;
    }

    if (leftDown) {
      // Keep following the finger that owns the drag, whatever order Android reports.
      final int index = indexOfId(dragPointerId >= 0 ? dragPointerId : ids[0]);
      if (index >= 0) {
        final float x = xs[index];
        final float y = ys[index];
        sink.pointer(x, y, x - lastX, y - lastY);
        lastX = x;
        lastY = y;
      }
      return;
    }

    computeCentroid();
    final float moved = (float) Math.hypot(prevCentroidX - startCentroidX,
                                           prevCentroidY - startCentroidY);
    final float spread = Math.abs(prevSpread - startSpread);

    if (state == STATE_TWO_UNDECIDED) {
      if (moved < decideSlop && spread < decideSlop) {
        return;
      }
      // Spread wins only when it is clearly ahead, so a two-finger drag that jitters the distance
      // does not zoom the view without warning.
      if (spread > moved * 1.2f) {
        if (!pinchZoomEnabled) {
          return;
        }
        state = STATE_ZOOM;
        gestureKind = GESTURE_ZOOM;
        gestureClassified = true;
        scrollFallback = false;
        startSpread = prevSpread;
        sink.gesture(GESTURE_ZOOM, true);
      } else {
        if (!twoFingerOrbit) {
          // Legacy fallback (native middle button unavailable): vertical scroll instead so the
          // gesture still does something useful on an older libblender.so.
          state = STATE_ZOOM;
          gestureKind = GESTURE_ZOOM;
          gestureClassified = true;
          scrollFallback = true;
          startCentroidY = prevCentroidY;
          sink.gesture(GESTURE_ZOOM, true);
        } else {
          state = STATE_ORBIT;
          gestureKind = GESTURE_ORBIT;
          gestureClassified = true;
          scrollFallback = false;
          lastX = prevCentroidX;
          lastY = prevCentroidY;
          sink.gesture(GESTURE_ORBIT, true);
          middleDown = true;
          sink.button(BUTTON_MIDDLE, true);
        }
      }
    }

    if (state == STATE_ZOOM) {
      if (scrollFallback) {
        // Scroll fallback: follow vertical centroid travel like a trackpad.
        final float scroll = (startCentroidY - prevCentroidY) * zoomNotchesPerPixel * 2f;
        startCentroidY = prevCentroidY;
        emitWheel(WHEEL_VERTICAL, scroll);
        return;
      }
      final float pinch = (prevSpread - startSpread) * zoomNotchesPerPixel;
      startSpread = prevSpread;
      emitWheel(WHEEL_VERTICAL, pinch);
      return;
    }

    if (state == STATE_ORBIT || state == STATE_PAN) {
      sink.pointer(prevCentroidX, prevCentroidY,
                   prevCentroidX - lastX, prevCentroidY - lastY);
      lastX = prevCentroidX;
      lastY = prevCentroidY;
    }
  }

  private void emitWheel(int axis, float amount) {
    zoomRemainder += amount;
    final int notches = (int) (zoomRemainder > 0f ? Math.floor(zoomRemainder)
                                                  : Math.ceil(zoomRemainder));
    if (notches == 0) {
      return;
    }
    /* The native bridge accumulates wheel values and clamps each event, so send small steps and
     * keep the rest for the next move instead of letting a fast pinch be clipped away. */
    final int capped = Math.max(-4, Math.min(4, notches));
    zoomRemainder -= capped;
    sink.wheel(axis, capped);
  }

  private void pointerUp(int index, long timeMs) {
    final int remaining = Math.max(0, count - 1);
    final boolean wasTapCandidate = state == STATE_TWO_UNDECIDED && !gestureClassified
        && timeMs - downAt <= tapTimeoutMs;

    if (remaining == 0) {
      // Handled by ACTION_UP, but Android may deliver POINTER_UP first for the last pointer.
      up(timeMs);
      return;
    }

    if (remaining == 1) {
      if (leftDown && ids[index] != dragPointerId) {
        // A secondary finger lifted while the drag finger is still down (a stray touch, or a
        // second finger that never became a gesture). Dropping the button here would abort the
        // user's transform mid-drag, so the drag simply resumes.
        state = STATE_DRAG;
        gestureClassified = false;
        return;
      }
      if (leftDown) {
        // The finger that was dragging left the glass: release before anything else so a
        // cancelled transform can never leave the left button stuck.
        sink.button(BUTTON_LEFT, false);
        leftDown = false;
        dragPointerId = -1;
        endNavigationGesture();
        state = STATE_IGNORE;
        gestureClassified = false;
        return;
      }
      endNavigationGesture();
      gestureClassified = false;
      if (wasTapCandidate && twoFingerTapRightClick) {
        // A second finger lifting does not cancel a two-finger tap: the last one completes it.
        secondTapPending = true;
        state = STATE_TWO_UNDECIDED;
        return;
      }
      state = STATE_IGNORE;
      return;
    }

    if (leftDown && ids[index] != dragPointerId && indexOfId(dragPointerId) >= 0) {
      // The drag finger survived; the extra fingers were incidental. Keep the drag.
      state = STATE_DRAG;
      gestureClassified = false;
      return;
    }

    // 3+ fingers -> 2: the pan is over; never continue as an orbit.
    endNavigationGesture();
    state = STATE_IGNORE;
    gestureClassified = false;
  }

  private void up(long timeMs) {
    if (state == STATE_PENDING) {
      // Plain tap: a real left click at the landing point.
      sink.pointer(originX, originY, originX - lastX, originY - lastY);
      sink.button(BUTTON_LEFT, true);
      sink.button(BUTTON_LEFT, false);
    } else if (leftDown) {
      if (state == STATE_DRAG) {
        sink.pointer(lastX, lastY, 0f, 0f);
      }
      sink.button(BUTTON_LEFT, false);
      dragPointerId = -1;
      leftDown = false;
    } else if (state == STATE_TWO_UNDECIDED && gestureClassified == false
        && secondTapPending && twoFingerTapRightClick) {
      // Two-finger tap: right click for Blender's context menus.
      sink.button(BUTTON_RIGHT, true);
      sink.button(BUTTON_RIGHT, false);
    }
    endNavigationGesture();
    resetState();
  }

  /** Releases every held mouse button and modifier; safe to call at any time. */
  public void releaseAll(String reason) {
    endNavigationGesture();
    dragPointerId = -1;
    if (leftDown) {
      leftDown = false;
      sink.button(BUTTON_LEFT, false);
    }
    resetState();
  }

  private void endNavigationGesture() {
    if (middleDown) {
      middleDown = false;
      sink.button(BUTTON_MIDDLE, false);
    }
    if (shiftDown) {
      shiftDown = false;
      sink.modifier(MODIFIER_SHIFT, false);
    }
    if (gestureKind >= 0) {
      sink.gesture(gestureKind, false);
      gestureKind = -1;
    }
  }

  private void resetState() {
    state = STATE_IDLE;
    count = 0;
    gestureClassified = false;
    scrollFallback = false;
    secondTapPending = false;
    zoomRemainder = 0f;
    leftDown = false;
    middleDown = false;
    shiftDown = false;
  }

  private void computeCentroid() {
    int used = Math.min(count, 3);
    float cx = 0f;
    float cy = 0f;
    for (int i = 0; i < used; i++) {
      cx += xs[i];
      cy += ys[i];
    }
    prevCentroidX = cx / used;
    prevCentroidY = cy / used;
    if (used >= 2) {
      prevSpread = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
    } else {
      prevSpread = 0f;
    }
  }
}
