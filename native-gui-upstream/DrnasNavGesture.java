package org.blender.blender;

/** Android-free orbit/pan state for the circular navigation dial. */
public final class DrnasNavGesture {
  public interface Sink {
    void begin(boolean pan);
    void move(float dx, float dy, boolean pan);
    void end(boolean pan);
  }

  private final Sink sink;
  private final float touchSlop;
  private float downX;
  private float downY;
  private float lastX;
  private float lastY;
  private boolean moved;
  private boolean active;
  private boolean pan;
  private boolean forcePan;

  public DrnasNavGesture(Sink sink, float touchSlop) {
    if (sink == null || touchSlop < 0f) throw new IllegalArgumentException();
    this.sink = sink;
    this.touchSlop = touchSlop;
  }

  public void down(float x, float y, int pointerCount) {
    down(x, y, pointerCount, false);
  }

  /** @param forcePan true for the navigation dial's dedicated pan sector. */
  public void down(float x, float y, int pointerCount, boolean forcePan) {
    downX = lastX = x;
    downY = lastY = y;
    moved = false;
    active = false;
    pan = false;
    this.forcePan = forcePan;
    if (pointerCount > 1) {
      moved = true;
      start(true);
    }
  }

  public void pointerCountChanged(int pointerCount) {
    if (pointerCount > 1) moved = true;
    if (moved && pointerCount > 0) transition(forcePan || pointerCount > 1);
  }

  public void move(float x, float y, int pointerCount) {
    if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) moved = true;
    if (moved && pointerCount > 0 && !active) start(forcePan || pointerCount > 1);
    else if (moved && active && pointerCount > 0) transition(forcePan || pointerCount > 1);
    if (active) sink.move(x - lastX, y - lastY, pan);
    lastX = x;
    lastY = y;
  }

  /** Ends any drag and returns true only for a tap (the caller resolves an axis view). */
  public boolean up() {
    if (active) sink.end(pan);
    final boolean tap = !moved;
    active = false;
    pan = false;
    forcePan = false;
    return tap;
  }

  public void cancel() {
    if (active) sink.end(pan);
    active = false;
    moved = true;
    pan = false;
    forcePan = false;
  }

  private void start(boolean asPan) {
    active = true;
    pan = asPan;
    sink.begin(pan);
  }

  private void transition(boolean asPan) {
    if (!active || pan == asPan) return;
    sink.end(pan);
    start(asPan);
  }
}
