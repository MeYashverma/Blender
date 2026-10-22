package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** One contiguous touch surface: a scroll rail above a three-zone Blender mouse. */
public final class DrnasMouseDockView extends TextView {
  public interface Listener {
    void button(int bit, boolean down);
    void wheel(float vertical, float horizontal);
  }

  private final SparseArray<Integer> scrollOwners = new SparseArray<>();
  private final SparseArray<Float> lastScrollX = new SparseArray<>();
  private final SparseArray<Float> lastScrollY = new SparseArray<>();
  private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final DrnasDockGesture gesture;
  private Listener listener;
  private boolean middleAvailable = true;
  private boolean horizontalAvailable = true;
  private float railHeight;
  private float wheelStep;

  public DrnasMouseDockView(Context context) {
    super(context);
    setText("");
    setFocusable(true);
    setClickable(true);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    railHeight = DrnasChrome.dp(this, 48f);
    wheelStep = DrnasChrome.dp(this, 18f);
    dividerPaint.setStyle(Paint.Style.STROKE);
    dividerPaint.setStrokeWidth(DrnasChrome.dp(this, 1f));
    dividerPaint.setColor(DrnasSkin.HAIRLINE_STRONG);
    gesture = new DrnasDockGesture((bit, down) -> {
      if (listener != null) listener.button(bit, down);
      invalidate();
      updateDescription();
    }, ViewConfiguration.getDoubleTapTimeout());
    updateDescription();
  }

  public void setListener(Listener value) { listener = value; }

  public void setMiddleAvailable(boolean available) {
    middleAvailable = available;
    if (!available && gesture.isHeld(DrnasMouseState.MIDDLE)) {
      gesture.releaseAll();
    }
    invalidate();
    updateDescription();
  }

  public void setHorizontalAvailable(boolean available) {
    horizontalAvailable = available;
    invalidate();
  }

  public void releaseAll() {
    gesture.releaseAll();
    scrollOwners.clear();
    lastScrollX.clear();
    lastScrollY.clear();
    invalidate();
    updateDescription();
  }

  private void updateDescription() {
    final String state = (gesture.isLatched(DrnasMouseState.LEFT) ? "left latched, " : "")
        + (gesture.isLatched(DrnasMouseState.MIDDLE) ? "middle latched, " : "")
        + (gesture.isLatched(DrnasMouseState.RIGHT) ? "right latched" : "");
    setContentDescription("Mouse dock. Upper rail scrolls vertically; swipe sideways for horizontal scroll. "
        + "Lower zones are left, middle, right mouse buttons. Double tap to latch. "
        + (state.isEmpty() ? "No buttons latched." : state));
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(getResources().getDisplayMetrics().density);
    final float inset = DrnasChrome.dp(this, 1f);
    final float radius = DrnasChrome.dp(this, DrnasSkin.RADIUS_GROUP);
    final RectF shell = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
    DrnasChrome.surface(canvas, shell, radius, DrnasSkin.SURFACE, true);

    final float pad = DrnasChrome.dp(this, 6f);
    final float innerLeft = shell.left + pad;
    final float innerRight = shell.right - pad;
    final float railTop = shell.top + pad;
    final float railBottom = railTop + railHeight;
    final float buttonsTop = railBottom + DrnasChrome.dp(this, 5f);
    final float bottom = shell.bottom - pad;
    final float zoneWidth = (innerRight - innerLeft) / 3f;

    // Single shallow scroll lane; it replaces the old row of four extra wheel buttons.
    final RectF rail = new RectF(innerLeft, railTop, innerRight, railBottom);
    DrnasChrome.surface(canvas, rail, DrnasChrome.dp(this, DrnasSkin.RADIUS_SMALL),
        DrnasSkin.WELL, false);
    DrnasChrome.icon(canvas, "wheel_up", innerLeft + zoneWidth * 0.42f,
        (railTop + railBottom) * 0.5f, DrnasChrome.dp(this, DrnasSkin.ICON_SM),
        DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    DrnasChrome.icon(canvas, "wheel_down", innerLeft + zoneWidth * 0.58f,
        (railTop + railBottom) * 0.5f, DrnasChrome.dp(this, DrnasSkin.ICON_SM),
        DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    if (horizontalAvailable) {
      DrnasChrome.icon(canvas, "wheel_left", innerLeft + zoneWidth * 1.42f,
          (railTop + railBottom) * 0.5f, DrnasChrome.dp(this, DrnasSkin.ICON_SM),
          DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
      DrnasChrome.icon(canvas, "wheel_right", innerLeft + zoneWidth * 1.58f,
          (railTop + railBottom) * 0.5f, DrnasChrome.dp(this, DrnasSkin.ICON_SM),
          DrnasSkin.ICON_SECONDARY, DrnasSkin.ICON_STROKE, 1f);
    }

    final float buttonHeight = Math.max(DrnasChrome.dp(this, DrnasSkin.TOUCH_MIN), bottom - buttonsTop);
    for (int zone = 0; zone < 3; zone++) {
      final int bit = zone == 0 ? DrnasMouseState.LEFT
          : zone == 1 ? DrnasMouseState.MIDDLE : DrnasMouseState.RIGHT;
      final float left = innerLeft + zone * zoneWidth;
      final RectF bounds = new RectF(left, buttonsTop, left + zoneWidth, buttonsTop + buttonHeight);
      if (zone > 0) {
        canvas.drawLine(left, buttonsTop + DrnasChrome.dp(this, 7f), left,
            buttonsTop + buttonHeight - DrnasChrome.dp(this, 7f), dividerPaint);
      }
      final boolean available = bit != DrnasMouseState.MIDDLE || middleAvailable;
      final boolean pressed = gesture.isHeld(bit) && !gesture.isLatched(bit);
      final boolean latched = gesture.isLatched(bit);
      if (pressed || latched) {
        final RectF active = new RectF(bounds.left + DrnasChrome.dp(this, 2f),
            bounds.top + DrnasChrome.dp(this, 2f), bounds.right - DrnasChrome.dp(this, 2f),
            bounds.bottom - DrnasChrome.dp(this, 2f));
        DrnasChrome.pill(canvas, active,
            latched ? DrnasSkin.ACCENT_WASH : DrnasSkin.SURFACE_PRESSED);
      }
      final String icon = zone == 0 ? DrnasIcons.LMB : zone == 1 ? DrnasIcons.MMB : DrnasIcons.RMB;
      DrnasChrome.icon(canvas, icon, bounds.centerX(), bounds.centerY(),
          DrnasChrome.dp(this, 22f), latched ? DrnasSkin.ACCENT_BRIGHT : DrnasSkin.ICON,
          DrnasSkin.ICON_STROKE, available ? 1f : DrnasSkin.ALPHA_DISABLED);
      if (latched) {
        DrnasChrome.circle(canvas, bounds.centerX(), bounds.bottom - DrnasChrome.dp(this, 6f),
            DrnasChrome.dp(this, 2f), DrnasSkin.ACCENT_BRIGHT, 0, 0);
      }
    }
  }

  private int zoneAt(float x) {
    final float pad = DrnasChrome.dp(this, 6f);
    final float usable = Math.max(1f, getWidth() - pad * 2f);
    final int zone = Math.min(2, Math.max(0, (int)((x - pad) / (usable / 3f))));
    return zone == 0 ? DrnasMouseState.LEFT : zone == 1 ? DrnasMouseState.MIDDLE : DrnasMouseState.RIGHT;
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
        if (y < railHeight + DrnasChrome.dp(this, 11f)) {
          scrollOwners.put(id, 1);
          lastScrollX.put(id, x);
          lastScrollY.put(id, y);
        } else {
          final int bit = zoneAt(x);
          if (bit != DrnasMouseState.MIDDLE || middleAvailable) {
            gesture.down(id, bit, event.getEventTime(), event.getPointerCount() == 1);
          }
        }
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        for (int p = 0; p < event.getPointerCount(); p++) {
          final int id = event.getPointerId(p);
          if (scrollOwners.get(id, 0) == 0) continue;
          final float x = event.getX(p);
          final float y = event.getY(p);
          float dx = x - lastScrollX.get(id, x);
          float dy = y - lastScrollY.get(id, y);
          int stepsY = (int)(Math.abs(dy) / wheelStep);
          int stepsX = horizontalAvailable ? (int)(Math.abs(dx) / wheelStep) : 0;
          if (stepsY > 0) {
            final float amount = Math.signum(-dy) * Math.min(stepsY, 6);
            if (listener != null) listener.wheel(amount, 0f);
            lastScrollY.put(id, y - (dy % wheelStep));
          }
          if (stepsX > 0) {
            final float amount = Math.signum(dx) * Math.min(stepsX, 6);
            if (listener != null) listener.wheel(0f, amount);
            lastScrollX.put(id, x - (dx % wheelStep));
          }
        }
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP: {
        final int id = event.getPointerId(index);
        if (scrollOwners.get(id, 0) != 0) {
          scrollOwners.remove(id);
          lastScrollX.remove(id);
          lastScrollY.remove(id);
        } else {
          gesture.up(id, event.getEventTime());
        }
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        gesture.cancel();
        scrollOwners.clear();
        lastScrollX.clear();
        lastScrollY.clear();
        invalidate();
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
