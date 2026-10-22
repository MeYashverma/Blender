package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Small Blender-style XYZ orientation puck; the viewport, not a panel of arrows, stays dominant. */
public final class DrnasOrientationGizmo extends View {

  public interface OnDirectionListener {
    void onDirection(int keyCode, int modifiers);
  }

  /** One-finger drag orbits; a two-finger drag pans using Blender's native MMB+Shift path. */
  public interface OnOrbitListener {
    void begin(boolean pan);
    void move(float dx, float dy, boolean pan);
    void end(boolean pan);
  }

  private OnDirectionListener listener;
  private OnOrbitListener orbitListener;
  private float downX;
  private float downY;
  private final DrnasNavGesture navGesture;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

  public DrnasOrientationGizmo(Context context) {
    super(context);
    setContentDescription("View orientation: red X, green Y, blue Z");
    setClickable(true);
    setFocusable(false);
    navGesture = new DrnasNavGesture(new DrnasNavGesture.Sink() {
      @Override public void begin(boolean pan) {
        if (orbitListener != null) orbitListener.begin(pan);
      }
      @Override public void move(float dx, float dy, boolean pan) {
        if (orbitListener != null) orbitListener.move(dx, dy, pan);
      }
      @Override public void end(boolean pan) {
        if (orbitListener != null) orbitListener.end(pan);
      }
    }, android.view.ViewConfiguration.get(context).getScaledTouchSlop());
  }

  public void setOnDirectionListener(OnDirectionListener value) {
    listener = value;
  }

  public void setOnOrbitListener(OnOrbitListener value) {
    orbitListener = value;
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(getResources().getDisplayMetrics().density);
    final float density = getResources().getDisplayMetrics().density;
    final float w = getWidth();
    final float h = getHeight();
    final float cx = w * 0.5f;
    final float cy = h * 0.5f;
    final float r = Math.min(w, h) * 0.43f;
    final float axis = r * 0.73f;

    // A matte, legible puck with a hairline edge, not a glowing glass button.
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(isPressed() ? DrnasSkin.SURFACE_RAISED : DrnasSkin.SURFACE);
    canvas.drawCircle(cx, cy, r, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(density);
    paint.setColor(DrnasSkin.HAIRLINE_STRONG);
    canvas.drawCircle(cx, cy, r, paint);

    // Three conventional axes, slightly offset to remain separable at phone scale.
    axis(canvas, cx, cy, cx + axis, cy, DrnasSkin.AXIS_X, density);
    axis(canvas, cx, cy, cx - axis, cy, DrnasSkin.AXIS_X, density);
    axis(canvas, cx, cy, cx + axis * 0.53f, cy - axis * 0.53f, DrnasSkin.AXIS_Y, density);
    axis(canvas, cx, cy, cx - axis * 0.53f, cy + axis * 0.53f, DrnasSkin.AXIS_Y, density);
    axis(canvas, cx, cy, cx, cy - axis, DrnasSkin.AXIS_Z, density);
    axis(canvas, cx, cy, cx, cy + axis, DrnasSkin.AXIS_Z, density);

    point(canvas, cx + axis, cy, DrnasSkin.AXIS_X, density);
    point(canvas, cx - axis, cy, DrnasSkin.AXIS_X, density);
    point(canvas, cx + axis * 0.53f, cy - axis * 0.53f, DrnasSkin.AXIS_Y, density);
    point(canvas, cx - axis * 0.53f, cy + axis * 0.53f, DrnasSkin.AXIS_Y, density);
    point(canvas, cx, cy - axis, DrnasSkin.AXIS_Z, density);
    point(canvas, cx, cy + axis, DrnasSkin.AXIS_Z, density);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(DrnasSkin.ICON);
    canvas.drawCircle(cx, cy, 2.2f * density, paint);
  }

  private void axis(Canvas canvas, float x1, float y1, float x2, float y2, int color,
                    float density) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.8f * density);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setColor(color);
    canvas.drawLine(x1, y1, x2, y2, paint);
  }

  private void point(Canvas canvas, float x, float y, int color, float density) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(DrnasSkin.SURFACE_RAISED);
    canvas.drawCircle(x, y, 5.3f * density, paint);
    paint.setColor(color);
    canvas.drawCircle(x, y, 3.5f * density, paint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = event.getX(0);
        downY = event.getY(0);
        navGesture.down(downX, downY, event.getPointerCount());
        setPressed(true);
        invalidate();
        return true;
      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_POINTER_UP:
        navGesture.pointerCountChanged(event.getPointerCount());
        return true;
      case MotionEvent.ACTION_MOVE:
        navGesture.move(event.getX(0), event.getY(0), event.getPointerCount());
        return true;
      case MotionEvent.ACTION_UP:
        setPressed(false);
        invalidate();
        if (navGesture.up() && listener != null) {
          final float density = getResources().getDisplayMetrics().density;
          final float cx = getWidth() * 0.5f;
          final float cy = getHeight() * 0.5f;
          final float dx = (event.getX() - cx) / density;
          final float dy = (event.getY() - cy) / density;
          final double angle = Math.atan2(dy, dx);
          final double ax = Math.abs(dx);
          final double ay = Math.abs(dy);
          final int key;
          final int modifiers;
          if (Math.hypot(dx, dy) < 8.0) {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_0;
            modifiers = 0;
          } else if (ay > ax * 1.45) {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_7;
            modifiers = dy < 0 ? 0 : android.view.KeyEvent.META_CTRL_ON;
          } else if (angle < -0.32 && angle > -1.28) {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_1;
            modifiers = 0;
          } else if (angle > 0.32 && angle < 1.28) {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_1;
            modifiers = android.view.KeyEvent.META_CTRL_ON;
          } else if (dx >= 0) {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_3;
            modifiers = 0;
          } else {
            key = android.view.KeyEvent.KEYCODE_NUMPAD_3;
            modifiers = android.view.KeyEvent.META_CTRL_ON;
          }
          listener.onDirection(key, modifiers);
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        navGesture.cancel();
        setPressed(false);
        invalidate();
        return true;
      default:
        return true;
    }
  }

}
