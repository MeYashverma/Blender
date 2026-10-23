package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Movable circular mouse surface modeled on a Blender pie controller, not a desktop button row. */
public final class DrnasMouseRingView extends TextView {
  public interface Listener {
    void button(int bit, boolean down);
    void wheel(float vertical, float horizontal);
    void interaction();
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path wedge = new Path();
  private final RectF circle = new RectF();
  private final SparseArray<Integer> scrollOwners = new SparseArray<>();
  private final SparseArray<Float> lastX = new SparseArray<>();
  private final SparseArray<Float> lastY = new SparseArray<>();
  private final float density;
  private final float wheelStep;
  private final DrnasDockGesture gesture;
  private Listener listener;
  private boolean middleAvailable = true;
  private boolean horizontalAvailable = true;

  public DrnasMouseRingView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    DrnasChrome.setDensity(density);
    wheelStep = dp(18f);
    gesture = new DrnasDockGesture((bit, down) -> {
      if (listener != null) listener.button(bit, down);
      invalidate();
      updateDescription();
    }, ViewConfiguration.getDoubleTapTimeout());
    setText("");
    setFocusable(true);
    setClickable(true);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    updateDescription();
  }

  public void setListener(Listener value) { listener = value; }

  public void setMiddleAvailable(boolean value) {
    middleAvailable = value;
    if (!value && gesture.isHeld(DrnasMouseState.MIDDLE)) gesture.releaseAll();
    invalidate();
    updateDescription();
  }

  public void setHorizontalAvailable(boolean value) {
    horizontalAvailable = value;
    invalidate();
  }

  public void releaseAll() {
    gesture.releaseAll();
    scrollOwners.clear();
    lastX.clear();
    lastY.clear();
    invalidate();
    updateDescription();
  }

  private float dp(float value) { return value * density; }

  private void updateDescription() {
    final String latched = (gesture.isLatched(DrnasMouseState.LEFT) ? "Left click locked. " : "")
        + (gesture.isLatched(DrnasMouseState.MIDDLE) ? "Middle click locked. " : "")
        + (gesture.isLatched(DrnasMouseState.RIGHT) ? "Right click locked. " : "");
    setContentDescription("Mouse ring: left click, middle click, right click and scroll. "
        + "Touch and hold a sector; double-tap a button to lock it. Swipe the top sector to scroll. "
        + latched + (middleAvailable ? "" : "Middle click unavailable in this native build."));
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(density);
    final float cx = getWidth() * 0.5f;
    final float cy = getHeight() * 0.5f;
    final float radius = Math.min(getWidth(), getHeight()) * 0.5f - dp(2f);
    circle.set(cx - radius, cy - radius, cx + radius, cy + radius);
    DrnasChrome.surface(canvas, circle, radius, DrnasSkin.SURFACE, true);

    drawSector(canvas, cx, cy, radius, -135f, 90f, isButtonActive(DrnasMouseState.LEFT));
    drawSector(canvas, cx, cy, radius, -45f, 90f, isButtonActive(DrnasMouseState.RIGHT));
    drawSector(canvas, cx, cy, radius, 45f, 90f, isButtonActive(DrnasMouseState.MIDDLE));
    drawSector(canvas, cx, cy, radius, 135f, 90f, false);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1f));
    paint.setColor(DrnasSkin.HAIRLINE_STRONG);
    final float hubRadius = dp(28f);
    for (int i = 0; i < 4; i++) {
      final double a = Math.toRadians(-135 + i * 90);
      canvas.drawLine(cx + (float)Math.cos(a) * hubRadius,
          cy + (float)Math.sin(a) * hubRadius,
          cx + (float)Math.cos(a) * (radius - dp(2f)),
          cy + (float)Math.sin(a) * (radius - dp(2f)), paint);
    }

    final float iconSize = dp(23f);
    DrnasChrome.icon(canvas, DrnasIcons.LMB, cx - radius * 0.62f, cy - dp(1f), iconSize,
        buttonColor(DrnasMouseState.LEFT), DrnasSkin.ICON_STROKE, 1f);
    drawLabel(canvas, "LEFT CLICK", cx - radius * 0.62f, cy + dp(24f),
        DrnasSkin.TEXT_SECONDARY, 8.5f);
    DrnasChrome.icon(canvas, DrnasIcons.RMB, cx + radius * 0.62f, cy - dp(1f), iconSize,
        buttonColor(DrnasMouseState.RIGHT), DrnasSkin.ICON_STROKE, 1f);
    drawLabel(canvas, "RIGHT CLICK", cx + radius * 0.62f, cy + dp(24f),
        DrnasSkin.TEXT_SECONDARY, 8.5f);
    DrnasChrome.icon(canvas, DrnasIcons.MMB, cx, cy + radius * 0.59f, iconSize,
        middleAvailable ? buttonColor(DrnasMouseState.MIDDLE) : DrnasSkin.ICON_DISABLED,
        DrnasSkin.ICON_STROKE, middleAvailable ? 1f : DrnasSkin.ALPHA_DISABLED);
    drawLabel(canvas, "MIDDLE CLICK", cx, cy + radius * 0.86f,
        middleAvailable ? DrnasSkin.TEXT_SECONDARY : DrnasSkin.TEXT_DISABLED, 8.5f);

    final float scrollY = cy - radius * 0.61f;
    DrnasChrome.icon(canvas, "wheel_up", cx - dp(13f), scrollY, dp(18f),
        DrnasSkin.ICON, DrnasSkin.ICON_STROKE, 1f);
    DrnasChrome.icon(canvas, "wheel_down", cx + dp(13f), scrollY, dp(18f),
        DrnasSkin.ICON, DrnasSkin.ICON_STROKE, 1f);
    if (horizontalAvailable) {
      DrnasChrome.icon(canvas, "wheel_left", cx - dp(22f), scrollY + dp(17f), dp(14f),
          DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
      DrnasChrome.icon(canvas, "wheel_right", cx + dp(22f), scrollY + dp(17f), dp(14f),
          DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    }
    drawLabel(canvas, "SCROLL", cx, cy - radius * 0.85f, DrnasSkin.TEXT, 8.5f);

    DrnasChrome.circle(canvas, cx, cy, hubRadius, DrnasSkin.SURFACE_RAISED,
        DrnasSkin.HAIRLINE_STRONG, dp(1f));
    DrnasChrome.icon(canvas, "mouse", cx, cy, dp(22f), DrnasSkin.ICON,
        DrnasSkin.ICON_STROKE, 1f);
    final int latched = gesture.latchedMask();
    if (latched != 0) {
      DrnasChrome.circle(canvas, cx + hubRadius * 0.62f, cy - hubRadius * 0.62f,
          dp(3f), DrnasSkin.ACCENT_BRIGHT, 0, 0);
    }
  }

  private void drawSector(Canvas canvas, float cx, float cy, float radius,
                          float start, float sweep, boolean active) {
    wedge.reset();
    wedge.moveTo(cx, cy);
    wedge.arcTo(circle, start, sweep);
    wedge.close();
    if (active) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(DrnasSkin.ACCENT_WASH);
      canvas.drawPath(wedge, paint);
    }
  }

  private boolean isButtonActive(int bit) {
    return gesture.isHeld(bit) || gesture.isLatched(bit);
  }

  private int buttonColor(int bit) {
    return isButtonActive(bit) ? DrnasSkin.ACCENT_BRIGHT : DrnasSkin.ICON;
  }

  private void drawLabel(Canvas canvas, String text, float x, float y, int color, float size) {
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    paint.setTextSize(dp(size));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setColor(color);
    canvas.drawText(text, x, y, paint);
  }

  private int zoneAt(float x, float y) {
    final float cx = getWidth() * 0.5f;
    final float cy = getHeight() * 0.5f;
    final float dx = x - cx;
    final float dy = y - cy;
    final float distance = (float)Math.sqrt(dx * dx + dy * dy);
    final float outer = Math.min(getWidth(), getHeight()) * 0.5f;
    if (distance < dp(29f) || distance > outer) return DrnasRadialPaletteModel.OUTSIDE;
    if (Math.abs(dy) >= Math.abs(dx)) return dy < 0f ? 0 : 2;
    return dx > 0f ? 1 : 3;
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
        final int zone = zoneAt(x, y);
        if (zone == DrnasRadialPaletteModel.OUTSIDE) return true;
        if (listener != null) listener.interaction();
        if (zone == 0) {
          scrollOwners.put(id, 1);
          lastX.put(id, x);
          lastY.put(id, y);
        } else {
          final int bit = zone == 1 ? DrnasMouseState.RIGHT
              : zone == 2 ? DrnasMouseState.MIDDLE : DrnasMouseState.LEFT;
          if (bit != DrnasMouseState.MIDDLE || middleAvailable) {
            gesture.down(id, bit, event.getEventTime(), event.getPointerCount() == 1);
          }
        }
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_MOVE:
        for (int p = 0; p < event.getPointerCount(); p++) {
          final int id = event.getPointerId(p);
          if (scrollOwners.get(id, 0) == 0) continue;
          final float x = event.getX(p);
          final float y = event.getY(p);
          final float dx = x - lastX.get(id, x);
          final float dy = y - lastY.get(id, y);
          final int stepsY = (int)(Math.abs(dy) / wheelStep);
          final int stepsX = horizontalAvailable ? (int)(Math.abs(dx) / wheelStep) : 0;
          if (stepsY > 0) {
            if (listener != null) listener.wheel(Math.signum(-dy) * Math.min(stepsY, 6), 0f);
            lastY.put(id, y - (dy % wheelStep));
          }
          if (stepsX > 0) {
            if (listener != null) listener.wheel(0f, Math.signum(dx) * Math.min(stepsX, 6));
            lastX.put(id, x - (dx % wheelStep));
          }
        }
        invalidate();
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP: {
        final int id = event.getPointerId(index);
        if (scrollOwners.get(id, 0) != 0) {
          scrollOwners.remove(id);
          lastX.remove(id);
          lastY.remove(id);
        } else {
          gesture.up(id, event.getEventTime());
        }
        invalidate();
        updateDescription();
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        gesture.cancel();
        scrollOwners.clear();
        lastX.clear();
        lastY.clear();
        invalidate();
        updateDescription();
        return true;
      default:
        return true;
    }
  }

  @Override public boolean performClick() {
    super.performClick();
    return true;
  }
}
