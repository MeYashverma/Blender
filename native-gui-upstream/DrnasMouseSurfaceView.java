package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** A real trackpad-like surface: cursor motion, two-finger scroll/pinch and a visible wheel rail. */
public final class DrnasMouseSurfaceView extends View {
  public interface Listener {
    void pointer(float dx, float dy);
    void wheel(float vertical, float horizontal);
    void click(int button);
    void interaction();
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final RectF pad = new RectF();
  private final RectF rail = new RectF();
  private final SparseArray<Integer> railOwners = new SparseArray<>();
  private final SparseArray<Float> railLastY = new SparseArray<>();
  private final DrnasMousePadGesture gesture;
  private final float density;
  private final float wheelStep;
  private Listener listener;
  private boolean horizontalAvailable = true;
  private int activeX;
  private int activeY;
  private boolean trackingPointer;

  public DrnasMouseSurfaceView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    DrnasChrome.setDensity(density);
    wheelStep = dp(28f);
    gesture = new DrnasMousePadGesture(new DrnasMousePadGesture.Sink() {
      @Override public void pointer(float dx, float dy) {
        if (listener != null) listener.pointer(dx, dy);
        invalidate();
      }
      @Override public void wheel(float vertical, float horizontal) {
        if (!horizontalAvailable) horizontal = 0f;
        if (listener != null && (vertical != 0f || horizontal != 0f)) {
          listener.wheel(vertical, horizontal);
        }
      }
      @Override public void click(int button) {
        if (listener != null) listener.click(button);
        performClick();
      }
    });
    gesture.configure(dp(32f), dp(30f), ViewConfiguration.get(context).getScaledTouchSlop(),
        ViewConfiguration.getTapTimeout());
    setFocusable(true);
    setClickable(true);
    setContentDescription("Mouse touchpad. One finger moves the pointer; tap for left click; "
        + "two fingers scroll or pinch to zoom; two-finger tap is right click. "
        + "The narrow right rail scrolls with one finger.");
  }

  public void setListener(Listener value) { listener = value; }

  public void setHorizontalAvailable(boolean value) {
    horizontalAvailable = value;
    invalidate();
  }

  public void releaseAll() {
    gesture.cancel();
    railOwners.clear();
    railLastY.clear();
    trackingPointer = false;
    invalidate();
  }

  private float dp(float value) { return value * density; }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(density);
    final float inset = dp(1f);
    bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
    DrnasChrome.surface(canvas, bounds, dp(12f), DrnasSkin.SURFACE, true);

    final float railWidth = Math.min(dp(34f), getWidth() * 0.22f);
    pad.set(dp(7f), dp(7f), getWidth() - railWidth - dp(5f), getHeight() - dp(7f));
    rail.set(pad.right + dp(2f), dp(7f), getWidth() - dp(7f), getHeight() - dp(7f));
    DrnasChrome.surface(canvas, pad, dp(9f), DrnasSkin.SURFACE_RAISED, false);
    DrnasChrome.surface(canvas, rail, dp(8f), DrnasSkin.SURFACE, false);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1f));
    paint.setColor(DrnasSkin.HAIRLINE);
    canvas.drawRoundRect(pad, dp(9f), dp(9f), paint);
    canvas.drawRoundRect(rail, dp(8f), dp(8f), paint);

    final float icon = dp(19f);
    DrnasChrome.icon(canvas, "mouse", pad.left + dp(17f), pad.top + dp(16f), icon,
        DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 0.9f);
    DrnasChrome.caption(canvas, "TRACKPAD", pad.left + dp(32f), pad.top + dp(19f),
        dp(8f), DrnasSkin.TEXT_SECONDARY);
    if (trackingPointer) {
      DrnasChrome.circle(canvas, activeX, activeY, dp(7f), DrnasSkin.ACCENT_WASH,
          DrnasSkin.ACCENT, dp(1f));
      DrnasChrome.icon(canvas, "move", activeX, activeY, dp(13f), DrnasSkin.ACCENT_BRIGHT,
          DrnasSkin.ICON_STROKE, 0.9f);
    } else {
      DrnasChrome.icon(canvas, "move", (pad.left + pad.right) * 0.5f,
          (pad.top + pad.bottom) * 0.58f, dp(22f), DrnasSkin.ICON_SECONDARY,
          DrnasSkin.ICON_STROKE, 0.42f);
      DrnasChrome.caption(canvas, "MOVE  •  TWO-FINGER SCROLL / PINCH",
          pad.left + dp(10f), pad.bottom - dp(10f), dp(7.5f), DrnasSkin.TEXT_DISABLED);
    }

    final float cx = (rail.left + rail.right) * 0.5f;
    DrnasChrome.icon(canvas, "wheel_up", cx, rail.top + dp(17f), dp(15f),
        DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    DrnasChrome.icon(canvas, "wheel_down", cx, rail.bottom - dp(17f), dp(15f),
        DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1.5f));
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setColor(DrnasSkin.HAIRLINE_STRONG);
    canvas.drawLine(cx, rail.top + dp(31f), cx, rail.bottom - dp(31f), paint);
    paint.setStyle(Paint.Style.FILL);
  }

  private boolean inRail(float x, float y) {
    return x >= rail.left && x <= rail.right && y >= rail.top && y <= rail.bottom;
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    final int action = event.getActionMasked();
    final int index = event.getActionIndex();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN: {
        final int id = event.getPointerId(index);
        final float x = event.getX(index);
        final float y = event.getY(index);
        if (listener != null) listener.interaction();
        if (inRail(x, y)) {
          railOwners.put(id, 1);
          railLastY.put(id, y);
        } else {
          trackingPointer = true;
          activeX = Math.round(x);
          activeY = Math.round(y);
          feed(event);
        }
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_MOVE:
        for (int p = 0; p < event.getPointerCount(); p++) {
          final int id = event.getPointerId(p);
          if (railOwners.get(id, 0) == 0) continue;
          final float y = event.getY(p);
          final float delta = railLastY.get(id, y) - y;
          final int notches = (int)(Math.abs(delta) / wheelStep);
          if (notches > 0 && listener != null) {
            listener.wheel(Math.signum(delta) * Math.min(notches, 6), 0f);
            railLastY.put(id, y - (delta % wheelStep));
          }
        }
        if (gesture.activePointerCount() > 0) {
          feed(event);
          final int first = firstSurfacePointer(event);
          if (first >= 0) {
            activeX = Math.round(event.getX(first));
            activeY = Math.round(event.getY(first));
          }
        }
        invalidate();
        return true;
      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_UP: {
        final int id = event.getPointerId(index);
        if (railOwners.get(id, 0) != 0) {
          railOwners.remove(id);
          railLastY.remove(id);
        } else {
          feed(event);
        }
        if (action == MotionEvent.ACTION_UP) trackingPointer = false;
        else if (gesture.activePointerCount() <= 1) trackingPointer = false;
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        releaseAll();
        return true;
      default:
        return true;
    }
  }

  private void feed(MotionEvent event) {
    final int[] ids = new int[event.getPointerCount()];
    final float[] xs = new float[event.getPointerCount()];
    final float[] ys = new float[event.getPointerCount()];
    int count = 0;
    int actionIndex = -1;
    final int leavingId = event.getPointerId(event.getActionIndex());
    for (int i = 0; i < event.getPointerCount(); i++) {
      final int id = event.getPointerId(i);
      if (railOwners.get(id, 0) != 0) continue;
      ids[count] = id;
      xs[count] = event.getX(i);
      ys[count] = event.getY(i);
      if (id == leavingId) actionIndex = count;
      count++;
    }
    final int rawAction = event.getActionMasked();
    final boolean down = rawAction == MotionEvent.ACTION_DOWN
        || rawAction == MotionEvent.ACTION_POINTER_DOWN;
    final boolean up = rawAction == MotionEvent.ACTION_UP
        || rawAction == MotionEvent.ACTION_POINTER_UP;
    if ((down || up) && actionIndex < 0) return;
    int action = actionFor(rawAction);
    if (down && gesture.activePointerCount() == 0) action = DrnasMousePadGesture.ACTION_DOWN;
    if (up && gesture.activePointerCount() <= 1) action = DrnasMousePadGesture.ACTION_UP;
    if (count == 0) return;
    gesture.onTouch(action, ids, xs, ys, Math.max(0, actionIndex), event.getEventTime());
  }

  private int firstSurfacePointer(MotionEvent event) {
    for (int i = 0; i < event.getPointerCount(); i++) {
      final int id = event.getPointerId(i);
      if (railOwners.get(id, 0) == 0) return i;
    }
    return -1;
  }

  private static int actionFor(int action) {
    switch (action) {
      case MotionEvent.ACTION_DOWN: return DrnasMousePadGesture.ACTION_DOWN;
      case MotionEvent.ACTION_POINTER_DOWN: return DrnasMousePadGesture.ACTION_POINTER_DOWN;
      case MotionEvent.ACTION_MOVE: return DrnasMousePadGesture.ACTION_MOVE;
      case MotionEvent.ACTION_POINTER_UP: return DrnasMousePadGesture.ACTION_POINTER_UP;
      case MotionEvent.ACTION_UP: return DrnasMousePadGesture.ACTION_UP;
      case MotionEvent.ACTION_CANCEL: return DrnasMousePadGesture.ACTION_CANCEL;
      default: return DrnasMousePadGesture.ACTION_CANCEL;
    }
  }

  @Override public boolean performClick() {
    super.performClick();
    return true;
  }
}
