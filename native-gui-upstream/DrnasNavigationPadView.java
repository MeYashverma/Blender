package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Circular navigation dial: direct orbit/pan in the hub with compact zoom/frame sectors. */
public final class DrnasNavigationPadView extends View {
  public static final int ZOOM_IN = 1;
  public static final int PAN_DRAG = 2;
  public static final int ZOOM_OUT = 3;
  public static final int FRAME_SELECTED = 4;

  public interface Listener {
    void command(int command);
    void begin(boolean pan);
    void move(float dx, float dy, boolean pan);
    void end(boolean pan);
    void interaction();
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path wedge = new Path();
  private final RectF circle = new RectF();
  private final float density;
  private final float touchSlop;
  private final DrnasNavGesture gesture;
  private Listener listener;
  private boolean gestureZone;
  private int commandZone = DrnasRadialPaletteModel.OUTSIDE;
  private float downX;
  private float downY;
  private int primaryPointerId = -1;
  private boolean moved;
  private boolean enabledMiddle = true;
  private boolean enabledShift = true;
  private boolean enabledKeys = true;

  public DrnasNavigationPadView(Context context) {
    super(context);
    density = getResources().getDisplayMetrics().density;
    DrnasChrome.setDensity(density);
    touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
    gesture = new DrnasNavGesture(new DrnasNavGesture.Sink() {
      @Override public void begin(boolean pan) {
        if (listener != null) listener.begin(pan);
      }
      @Override public void move(float dx, float dy, boolean pan) {
        if (listener != null) listener.move(dx, dy, pan);
      }
      @Override public void end(boolean pan) {
        if (listener != null) listener.end(pan);
      }
    }, touchSlop);
    setClickable(true);
    setFocusable(true);
    updateDescription();
  }

  public void setListener(Listener value) { listener = value; }
  public void setCapabilities(boolean middle, boolean shift, boolean keys) {
    enabledMiddle = middle;
    enabledShift = shift;
    enabledKeys = keys;
    invalidate();
    updateDescription();
  }
  public void releaseAll() { gesture.cancel(); invalidate(); }

  private float dp(float value) { return value * density; }

  private void updateDescription() {
    setContentDescription("Navigation dial. Drag the center to orbit; use two fingers or the right "
        + "pan sector to pan. Top zooms in, bottom zooms out, left frames selection. "
        + (enabledMiddle ? "" : "Orbit and pan require native middle-button support. ")
        + (enabledShift ? "" : "Pan requires native Shift support. ")
        + (enabledKeys ? "" : "Frame requires native key support."));
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    DrnasChrome.setDensity(density);
    final float cx = getWidth() * 0.5f;
    final float cy = getHeight() * 0.5f;
    final float radius = Math.min(getWidth(), getHeight()) * 0.5f - dp(2f);
    circle.set(cx - radius, cy - radius, cx + radius, cy + radius);
    DrnasChrome.surface(canvas, circle, radius, DrnasSkin.SURFACE, true);

    drawSector(canvas, cx, cy, radius, -135f, 90f, 0);
    drawSector(canvas, cx, cy, radius, -45f, 90f, 1);
    drawSector(canvas, cx, cy, radius, 45f, 90f, 2);
    drawSector(canvas, cx, cy, radius, 135f, 90f, 3);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1f));
    paint.setColor(DrnasSkin.HAIRLINE_STRONG);
    final float inner = dp(37f);
    for (int i = 0; i < 4; i++) {
      final double a = Math.toRadians(-135 + i * 90);
      canvas.drawLine(cx + (float)Math.cos(a) * inner, cy + (float)Math.sin(a) * inner,
          cx + (float)Math.cos(a) * (radius - dp(2f)),
          cy + (float)Math.sin(a) * (radius - dp(2f)), paint);
    }

    final float iconRadius = radius * 0.68f;
    drawAction(canvas, "zoom_in", "ZOOM IN", cx, cy, 0, iconRadius, true);
    drawAction(canvas, "pan", "PAN", cx, cy, 1, iconRadius, enabledMiddle && enabledShift);
    drawAction(canvas, "zoom_out", "ZOOM OUT", cx, cy, 2, iconRadius, true);
    drawAction(canvas, "frame", "FRAME", cx, cy, 3, iconRadius, enabledKeys);

    DrnasChrome.circle(canvas, cx, cy, inner - dp(2f), DrnasSkin.SURFACE_RAISED,
        DrnasSkin.HAIRLINE_STRONG, dp(1f));
    DrnasChrome.icon(canvas, "orbit", cx, cy - dp(3f), dp(25f),
        enabledMiddle ? DrnasSkin.ICON : DrnasSkin.ICON_DISABLED,
        DrnasSkin.ICON_STROKE, enabledMiddle ? 1f : DrnasSkin.ALPHA_DISABLED);
    drawLabel(canvas, "ORBIT", cx, cy + dp(21f), DrnasSkin.TEXT_SECONDARY, 8.5f);
  }

  private void drawSector(Canvas canvas, float cx, float cy, float radius,
                          float start, float sweep, int sector) {
    wedge.reset();
    wedge.moveTo(cx, cy);
    wedge.arcTo(circle, start, sweep);
    wedge.close();
    final int zone = sector == 0 ? ZOOM_IN : sector == 1 ? PAN_DRAG
        : sector == 2 ? ZOOM_OUT : FRAME_SELECTED;
    if (commandZone == zone) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(DrnasSkin.ACCENT_WASH);
      canvas.drawPath(wedge, paint);
    }
  }

  private void drawAction(Canvas canvas, String icon, String label, float cx, float cy,
                          int sector, float radius, boolean available) {
    final double a = Math.toRadians(-90 + sector * 90);
    final float x = cx + (float)Math.cos(a) * radius;
    final float y = cy + (float)Math.sin(a) * radius;
    DrnasChrome.icon(canvas, icon, x, y - dp(5f), dp(21f),
        available ? DrnasSkin.ICON : DrnasSkin.ICON_DISABLED,
        DrnasSkin.ICON_STROKE, available ? 1f : DrnasSkin.ALPHA_DISABLED);
    drawLabel(canvas, label, x, y + dp(17f),
        available ? DrnasSkin.TEXT_SECONDARY : DrnasSkin.TEXT_DISABLED, 8f);
  }

  private void drawLabel(Canvas canvas, String label, float x, float y, int color, float size) {
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(dp(size));
    paint.setColor(color);
    canvas.drawText(label, x, y, paint);
  }

  private int zoneAt(float x, float y) {
    final float cx = getWidth() * 0.5f;
    final float cy = getHeight() * 0.5f;
    final float dx = x - cx;
    final float dy = y - cy;
    final float r = (float)Math.sqrt(dx * dx + dy * dy);
    final float outer = Math.min(getWidth(), getHeight()) * 0.5f;
    if (r < dp(37f)) return DrnasRadialPaletteModel.CENTER;
    if (r > outer) return DrnasRadialPaletteModel.OUTSIDE;
    if (Math.abs(dy) >= Math.abs(dx)) return dy < 0f ? ZOOM_IN : ZOOM_OUT;
    return dx > 0f ? PAN_DRAG : FRAME_SELECTED;
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    final int action = event.getActionMasked();
    final int index = event.getActionIndex();
    switch (action) {
      case MotionEvent.ACTION_DOWN: {
        final float x = event.getX(index);
        final float y = event.getY(index);
        final int zone = zoneAt(x, y);
        if (zone == DrnasRadialPaletteModel.OUTSIDE) return false;
        if (listener != null) listener.interaction();
        gestureZone = zone == DrnasRadialPaletteModel.CENTER || zone == PAN_DRAG;
        commandZone = zone;
        primaryPointerId = event.getPointerId(index);
        downX = x;
        downY = y;
        moved = false;
        if (gestureZone) gesture.down(x, y, 1, zone == PAN_DRAG);
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_POINTER_DOWN:
        if (gestureZone) gesture.pointerCountChanged(event.getPointerCount());
        return true;
      case MotionEvent.ACTION_MOVE:
        if (gestureZone) {
          final int pointer = event.findPointerIndex(primaryPointerId);
          if (pointer >= 0) gesture.move(event.getX(pointer), event.getY(pointer), event.getPointerCount());
          else { gesture.cancel(); gestureZone = false; }
        } else {
          final int pointer = event.findPointerIndex(primaryPointerId);
          if (pointer >= 0) {
            moved |= Math.abs(event.getX(pointer) - downX) > touchSlop
                || Math.abs(event.getY(pointer) - downY) > touchSlop;
          }
        }
        invalidate();
        return true;
      case MotionEvent.ACTION_POINTER_UP:
        if (event.getPointerId(index) == primaryPointerId) {
          gesture.cancel();
          gestureZone = false;
          commandZone = DrnasRadialPaletteModel.OUTSIDE;
          primaryPointerId = -1;
        } else if (gestureZone) {
          gesture.pointerCountChanged(Math.max(1, event.getPointerCount() - 1));
        }
        return true;
      case MotionEvent.ACTION_UP:
        if (gestureZone) {
          gesture.move(event.getX(index), event.getY(index), 1);
          gesture.up();
        } else if (!moved && commandZone >= ZOOM_IN && listener != null) {
          if (commandZone == FRAME_SELECTED && !enabledKeys) {
            // Disabled functions stay visibly inert; no wrong native event is sent.
          } else {
            listener.command(commandZone);
          }
        }
        gestureZone = false;
        commandZone = DrnasRadialPaletteModel.OUTSIDE;
        primaryPointerId = -1;
        invalidate();
        return true;
      case MotionEvent.ACTION_CANCEL:
        gesture.cancel();
        gestureZone = false;
        commandZone = DrnasRadialPaletteModel.OUTSIDE;
        primaryPointerId = -1;
        moved = true;
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
