package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** One independent, accessible Blender mouse button in the floating controller. */
public final class DrnasMouseButtonView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF bounds = new RectF();
  private final float density;
  private final int bit;
  private final String icon;
  private final String label;
  private final DrnasMouseButtonModel model;
  private int pointerId = -1;
  private boolean dispatchingTouchClick;

  public DrnasMouseButtonView(Context context, int button, DrnasMouseButtonModel state) {
    super(context);
    if (button != DrnasMouseState.LEFT && button != DrnasMouseState.MIDDLE
        && button != DrnasMouseState.RIGHT) throw new IllegalArgumentException("button");
    if (state == null) throw new IllegalArgumentException("state");
    bit = button;
    model = state;
    icon = button == DrnasMouseState.LEFT ? DrnasIcons.LMB
        : button == DrnasMouseState.MIDDLE ? DrnasIcons.MMB : DrnasIcons.RMB;
    label = button == DrnasMouseState.LEFT ? "LMB"
        : button == DrnasMouseState.MIDDLE ? "MMB" : "RMB";
    density = getResources().getDisplayMetrics().density;
    DrnasChrome.setDensity(density);
    setFocusable(true);
    setClickable(true);
    setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    setContentDescription(label + ". Touch and hold to hold the mouse button; double-tap to latch. "
        + "Middle button is capability-gated.");
  }

  private float dp(float value) { return value * density; }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(density);
    final boolean enabled = bit != DrnasMouseState.MIDDLE || isEnabled();
    final boolean active = model.isHeld(bit);
    bounds.set(dp(2f), dp(2f), getWidth() - dp(2f), getHeight() - dp(2f));
    final int fill = active ? DrnasSkin.ACCENT_WASH : DrnasSkin.SURFACE_RAISED;
    DrnasChrome.surface(canvas, bounds, dp(9f), fill, true);
    final float alpha = enabled ? (active ? 1f : 0.82f) : DrnasSkin.ALPHA_DISABLED;
    final int iconColor = enabled
        ? (active ? DrnasSkin.ACCENT_BRIGHT : DrnasSkin.ICON)
        : DrnasSkin.ICON_DISABLED;
    final float iconSize = Math.min(dp(19f), getHeight() * 0.40f);
    DrnasChrome.icon(canvas, icon, getWidth() * 0.5f, getHeight() * 0.38f,
        iconSize, iconColor, DrnasSkin.ICON_STROKE, alpha);
    DrnasChrome.caption(canvas, label, getWidth() * 0.5f - DrnasChrome.measure(label, dp(9f), true) * 0.5f,
        getHeight() - dp(10f), dp(9f), enabled ? DrnasSkin.TEXT_SECONDARY : DrnasSkin.TEXT_DISABLED);
    if (model.isLatched(bit)) {
      DrnasChrome.circle(canvas, getWidth() - dp(10f), dp(10f), dp(3f),
          DrnasSkin.ACCENT_BRIGHT, 0, 0);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    final int action = event.getActionMasked();
    final int index = event.getActionIndex();
    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        if (!isEnabled()) return true;
        pointerId = event.getPointerId(index);
        model.down(pointerId, bit, event.getEventTime(), event.getPointerCount() == 1);
        invalidate();
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        if (event.getPointerId(index) == pointerId) {
          model.up(pointerId, event.getEventTime());
          pointerId = -1;
          dispatchingTouchClick = true;
          performClick();
          dispatchingTouchClick = false;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        if (pointerId >= 0) model.cancelPointer(pointerId);
        pointerId = -1;
        invalidate();
        return true;
      default:
        return true;
    }
  }

  @Override public boolean performClick() {
    super.performClick();
    if (!dispatchingTouchClick && isEnabled()) model.click(bit);
    return true;
  }
}
