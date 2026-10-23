package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Purpose-aware Blender pie menu rendered as a light, touch-first radial surface. */
public final class DrnasRadialPaletteView extends View {
  public interface Listener {
    void action(int actionId);
    void pageChanged(int page);
    void interaction();
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF circle = new RectF();
  private final Path wedge = new Path();
  private final float density;
  private final float touchSlop;
  private Listener listener;
  private int page;
  private int selected = DrnasRadialPaletteModel.OUTSIDE;
  private int pointerId = -1;
  private float downX;
  private float downY;
  private boolean moved;

  public DrnasRadialPaletteView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    DrnasChrome.setDensity(density);
    touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    setFocusable(true);
    setClickable(true);
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    updateDescription();
  }

  public void setListener(Listener value) { listener = value; }
  public int page() { return page; }

  public void setPage(int value) {
    page = Math.floorMod(value, DrnasRadialPaletteModel.PAGE_COUNT);
    selected = DrnasRadialPaletteModel.OUTSIDE;
    updateDescription();
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(density);
    final float cx = getWidth() * 0.5f;
    final float cy = getHeight() * 0.5f;
    final float radius = Math.min(getWidth(), getHeight()) * 0.5f - dp(2f);
    circle.set(cx - radius, cy - radius, cx + radius, cy + radius);
    DrnasChrome.surface(canvas, circle, radius, DrnasSkin.SURFACE, true);

    // Eight shallow sectors keep the actions readable without turning the viewport into a board.
    for (int slot = 0; slot < DrnasRadialPaletteModel.SLOT_COUNT; slot++) {
      final float start = -112.5f + slot * 45f;
      wedge.reset();
      wedge.moveTo(cx, cy);
      wedge.arcTo(circle, start, 45f);
      wedge.close();
      if (selected == slot) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DrnasSkin.ACCENT_WASH);
        canvas.drawPath(wedge, paint);
      }
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(0.8f));
      paint.setColor(DrnasSkin.HAIRLINE_STRONG);
      final double angle = Math.toRadians(-90d + slot * 45d);
      final float dividerInner = dp(31f);
      final float dividerOuter = radius - dp(3f);
      canvas.drawLine(cx + (float)Math.cos(angle) * dividerInner,
          cy + (float)Math.sin(angle) * dividerInner,
          cx + (float)Math.cos(angle) * dividerOuter,
          cy + (float)Math.sin(angle) * dividerOuter, paint);

      final DrnasRadialPaletteModel.Item item = DrnasRadialPaletteModel.itemAt(page, slot);
      final float iconRadius = radius * 0.57f;
      final float ix = cx + (float)Math.cos(angle) * iconRadius;
      final float iy = cy + (float)Math.sin(angle) * iconRadius;
      DrnasChrome.icon(canvas, item.icon, ix, iy, dp(19f),
          selected == slot ? DrnasSkin.ACCENT_BRIGHT : DrnasSkin.ICON,
          DrnasSkin.ICON_STROKE, 1f);
      final float labelRadius = radius * 0.82f;
      drawLabel(canvas, shortLabel(item.label),
          cx + (float)Math.cos(angle) * labelRadius,
          cy + (float)Math.sin(angle) * labelRadius + dp(3f),
          selected == slot ? DrnasSkin.TEXT : DrnasSkin.TEXT_SECONDARY);
    }

    final float hub = dp(28f);
    DrnasChrome.circle(canvas, cx, cy, hub,
        selected == DrnasRadialPaletteModel.CENTER ? DrnasSkin.SURFACE_PRESSED
            : DrnasSkin.SURFACE_RAISED,
        DrnasSkin.HAIRLINE_STRONG, dp(1f));
    drawLabel(canvas, shortPageName(page), cx, cy + dp(3f), DrnasSkin.TEXT);
    updateDescription();
  }

  private void drawLabel(Canvas canvas, String label, float x, float y, int color) {
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    paint.setTextSize(dp(9f));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setColor(color);
    canvas.drawText(label, x, y, paint);
  }

  private float dp(float value) { return value * density; }

  private static String shortLabel(String label) {
    switch (label) {
      case "Duplicate": return "DUP";
      case "Loop cut": return "LOOP";
      case "Prev frame": return "PREV";
      case "Next frame": return "NEXT";
      case "Keyframe": return "KEY";
      case "Frame all": return "ALL";
      case "Persp/ortho": return "ORTHO";
      default: return label.toUpperCase(java.util.Locale.ROOT);
    }
  }

  private static String shortPageName(int value) {
    switch (value) {
      case DrnasRadialPaletteModel.PAGE_EDIT: return "EDIT";
      case DrnasRadialPaletteModel.PAGE_TIMELINE: return "TIME";
      case DrnasRadialPaletteModel.PAGE_VIEW: return "VIEW";
      default: return "OBJECT";
    }
  }

  private void updateDescription() {
    final String purpose = DrnasRadialPaletteModel.pageName(page);
    final String action = selected >= 0
        ? ". Preview: " + DrnasRadialPaletteModel.itemAt(page, selected).label : "";
    setContentDescription(purpose + " tool palette. Eight radial actions; drag to an action and lift. "
        + "Tap the center to switch to the next purpose page." + action);
  }

  private int hit(float x, float y) {
    final float radius = Math.min(getWidth(), getHeight()) * 0.5f - dp(2f);
    return DrnasRadialPaletteModel.hitSlot(x, y, getWidth() * 0.5f, getHeight() * 0.5f,
        dp(28f), radius);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    final int action = event.getActionMasked();
    final int index = event.getActionIndex();
    switch (action) {
      case MotionEvent.ACTION_DOWN: {
        final int slot = hit(event.getX(index), event.getY(index));
        if (slot == DrnasRadialPaletteModel.OUTSIDE) return false;
        pointerId = event.getPointerId(index);
        downX = event.getX(index);
        downY = event.getY(index);
        moved = false;
        selected = slot;
        if (listener != null) listener.interaction();
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_MOVE: {
        if (pointerId < 0) return false;
        final int pointer = event.findPointerIndex(pointerId);
        if (pointer < 0) return true;
        final float x = event.getX(pointer);
        final float y = event.getY(pointer);
        moved |= Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop;
        selected = hit(x, y);
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP: {
        if (event.getPointerId(index) != pointerId) return true;
        final int slot = hit(event.getX(index), event.getY(index));
        selected = slot;
        if (slot == DrnasRadialPaletteModel.CENTER) {
          setPage(DrnasRadialPaletteModel.nextPage(page));
          if (listener != null) listener.pageChanged(page);
        } else if (slot >= 0 && listener != null) {
          listener.action(DrnasRadialPaletteModel.itemAt(page, slot).id);
          performClick();
        }
        pointerId = -1;
        selected = DrnasRadialPaletteModel.OUTSIDE;
        invalidate();
        updateDescription();
        return true;
      }
      case MotionEvent.ACTION_CANCEL:
        pointerId = -1;
        selected = DrnasRadialPaletteModel.OUTSIDE;
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
