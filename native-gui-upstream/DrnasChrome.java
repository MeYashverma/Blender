package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Drawing primitives shared by every control in the layer.
 *
 * <p>Surfaces, hairlines, icons and labels are painted here so that no control can drift from the
 * design: same corner radius, same stroke weight, same type ramp, same anti-aliasing. Icons are
 * parsed from {@link DrnasIcons} path data once per size and cached, so drawing a glyph costs a
 * canvas transform and a drawPath.
 */
final class DrnasChrome {

  private DrnasChrome() {
  }

  private static final Map<String, Path> PATHS = new HashMap<>();

  private static Paint fill;
  private static Paint stroke;
  private static Paint text;

  static float dp(Context context, float value) {
    return value * context.getResources().getDisplayMetrics().density;
  }

  static float dp(View view, float value) {
    return dp(view.getContext(), value);
  }

  static float saturate(float value) {
    return value < 0f ? 0f : (value > 1f ? 1f : value);
  }

  /** Decelerating interpolation, used for press and fade transitions. */
  static float easeOut(float t) {
    final float c = saturate(t);
    return 1f - (1f - c) * (1f - c);
  }

  /** Progress of a time-based transition, 0..1. */
  static float progress(long startedAt, long duration) {
    if (duration <= 0L) {
      return 1f;
    }
    final float t = (android.os.SystemClock.uptimeMillis() - startedAt) / (float) duration;
    return saturate(t);
  }

  private static Paint fillPaint(int color) {
    if (fill == null) {
      fill = new Paint(Paint.ANTI_ALIAS_FLAG);
      fill.setStyle(Paint.Style.FILL);
    }
    fill.setColor(color);
    return fill;
  }

  private static Paint strokePaint(int color, float width) {
    if (stroke == null) {
      stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
      stroke.setStyle(Paint.Style.STROKE);
      stroke.setStrokeCap(Paint.Cap.ROUND);
      stroke.setStrokeJoin(Paint.Join.ROUND);
    }
    stroke.setColor(color);
    stroke.setStrokeWidth(width);
    return stroke;
  }

  private static Paint textPaint(float sizePx, int color, boolean medium) {
    if (text == null) {
      text = new Paint(Paint.ANTI_ALIAS_FLAG);
      text.setTextAlign(Paint.Align.LEFT);
    }
    text.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    text.setTextSize(sizePx);
    text.setColor(color);
    return text;
  }

  /**
   * A floating control surface: one rounded rectangle, a hairline edge instead of a drop shadow,
   * and an optional inner top highlight that separates it from the viewport without glassiness.
   */
  static void surface(Canvas canvas, RectF bounds, float radiusPx, int fillColor, boolean hairline) {
    canvas.drawRoundRect(bounds, radiusPx, radiusPx, fillPaint(fillColor));
    if (!hairline) {
      return;
    }
    final float inset = 0.5f;
    final RectF edge = new RectF(bounds.left + inset, bounds.top + inset,
        bounds.right - inset, bounds.bottom - inset);
    canvas.drawRoundRect(edge, radiusPx - inset, radiusPx - inset,
        strokePaint(DrnasSkin.HAIRLINE, DrnasChrome.dpWidth(1f)));
  }

  /** Hairline thickness in pixels for the current density, stored per draw via the paint width. */
  private static float density = 1f;

  static void setDensity(float value) {
    density = Math.max(1f, value);
  }

  static float dpWidth(float dp) {
    return dp * density;
  }

  /** Rounded pill, used for latches, badges and segmented selections. */
  static void pill(Canvas canvas, RectF bounds, int color) {
    canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, fillPaint(color));
  }

  /** Circle with an optional outline. */
  static void circle(Canvas canvas, float cx, float cy, float radius, int fillColor,
                     int strokeColor, float strokeWidth) {
    if ((fillColor >>> 24) != 0) {
      canvas.drawCircle(cx, cy, radius, fillPaint(fillColor));
    }
    if ((strokeColor >>> 24) != 0 && strokeWidth > 0f) {
      canvas.drawCircle(cx, cy, radius, strokePaint(strokeColor, strokeWidth));
    }
  }

  /** Parsed icon geometry, cached per name and pixel size. */
  private static Path iconPath(String name, float sizePx) {
    final String key = name + '@' + Math.round(sizePx);
    Path cached = PATHS.get(key);
    if (cached != null) {
      return cached;
    }
    final String data = DrnasIcons.stroke(name);
    if (data == null) {
      return null;
    }
    cached = parse(data, sizePx / DrnasIcons.VIEWBOX);
    PATHS.put(key, cached);
    return cached;
  }

  private static Path iconFill(String name, float sizePx) {
    final String key = name + "*@" + Math.round(sizePx);
    Path cached = PATHS.get(key);
    if (cached != null) {
      return cached;
    }
    final String data = DrnasIcons.fill(name);
    if (data == null) {
      return null;
    }
    cached = parse(data, sizePx / DrnasIcons.VIEWBOX);
    PATHS.put(key, cached);
    return cached;
  }

  /**
   * Draws an icon centred on {@code cx, cy}. {@code alpha} scales the colour so press and disabled
   * states stay consistent across every surface.
   */
  static void icon(Canvas canvas, String name, float cx, float cy, float sizePx, int color,
                   float strokeDp, float alpha) {
    if (name == null || name.length() == 0) {
      return;
    }
    final int paintColor = DrnasSkin.withAlphaScale(color, alpha);
    if ((paintColor >>> 24) == 0) {
      return;
    }
    final float half = sizePx / 2f;
    canvas.save();
    canvas.translate(cx - half, cy - half);
    final Path solid = iconFill(name, sizePx);
    if (solid != null) {
      canvas.drawPath(solid, fillPaint(paintColor));
    }
    final Path outline = iconPath(name, sizePx);
    if (outline != null) {
      canvas.drawPath(outline, strokePaint(paintColor, Math.max(1f, strokeDp * density)));
    }
    canvas.restore();
  }

  /** Left-aligned label; 9sp caption .. 12sp row title. */
  static void text(Canvas canvas, String value, float x, float baseline, float sizePx, int color,
                   boolean medium) {
    if (value == null || value.length() == 0) {
      return;
    }
    canvas.drawText(value, x, baseline, textPaint(sizePx, color, medium));
  }

  static float measure(String value, float sizePx, boolean medium) {
    if (value == null) {
      return 0f;
    }
    return textPaint(sizePx, 0xFFFFFFFF, medium).measureText(value);
  }

  /** Small letterspaced caption used for section headers ("INPUT", "GESTURES"). */
  static void caption(Canvas canvas, String value, float x, float baseline, float sizePx, int color) {
    // Uppercase section titles are already letterspaced by their concise labels; avoid platform
    // Paint letter-spacing APIs so this remains compatible with the pinned minimum Android SDK.
    canvas.drawText(value, x, baseline, textPaint(sizePx, color, true));
  }

  /**
   * Minimal SVG path parser for the icon data: M/m, L/l, H/h, V/v, C/c, Q/q and Z/z. Anything else
   * throws in the unit test, never in the UI, because the data is static.
   */
  static Path parse(String data, float scale) {
    final Path path = new Path();
    int i = 0;
    final int n = data.length();
    float currentX = 0f;
    float currentY = 0f;
    float startX = 0f;
    float startY = 0f;
    char command = 'M';
    while (i < n) {
      final char c = data.charAt(i);
      if (c == ' ') {
        i++;
        continue;
      }
      if (Character.isLetter(c)) {
        command = c;
        i++;
        if (command == 'Z' || command == 'z') {
          path.close();
          currentX = startX;
          currentY = startY;
          command = 'M';
        }
        continue;
      }
      final float[] args = new float[6];
      int count = 0;
      // Read as many numbers as this command needs, in one pass.
      final int need = needs(command);
      while (count < need) {
        // Skip separators
        while (i < n && (data.charAt(i) == ' ' || data.charAt(i) == ',')) {
          i++;
        }
        final int begin = i;
        if (i < n && (data.charAt(i) == '-' || data.charAt(i) == '+')) {
          i++;
        }
        while (i < n && (Character.isDigit(data.charAt(i)) || data.charAt(i) == '.')) {
          i++;
        }
        if (i == begin) {
          break;
        }
        args[count++] = Float.parseFloat(data.substring(begin, i)) * scale;
      }
      if (count < need) {
        break;
      }
      switch (command) {
        case 'M':
          path.moveTo(args[0], args[1]);
          currentX = args[0];
          currentY = args[1];
          startX = currentX;
          startY = currentY;
          command = 'L'; // implicit lineto for following pairs
          break;
        case 'm':
          path.rMoveTo(args[0], args[1]);
          currentX += args[0];
          currentY += args[1];
          startX = currentX;
          startY = currentY;
          command = 'l';
          break;
        case 'L':
          path.lineTo(args[0], args[1]);
          currentX = args[0];
          currentY = args[1];
          break;
        case 'l':
          path.rLineTo(args[0], args[1]);
          currentX += args[0];
          currentY += args[1];
          break;
        case 'H':
          path.lineTo(args[0], currentY);
          currentX = args[0];
          break;
        case 'h':
          path.rLineTo(args[0], 0f);
          currentX += args[0];
          break;
        case 'V':
          path.lineTo(currentX, args[0]);
          currentY = args[0];
          break;
        case 'v':
          path.rLineTo(0f, args[0]);
          currentY += args[0];
          break;
        case 'C':
          path.cubicTo(args[0], args[1], args[2], args[3], args[4], args[5]);
          currentX = args[4];
          currentY = args[5];
          break;
        case 'c':
          path.rCubicTo(args[0], args[1], args[2], args[3], args[4], args[5]);
          currentX += args[4];
          currentY += args[5];
          break;
        case 'Q':
          path.quadTo(args[0], args[1], args[2], args[3]);
          currentX = args[2];
          currentY = args[3];
          break;
        case 'q':
          path.rQuadTo(args[0], args[1], args[2], args[3]);
          currentX += args[2];
          currentY += args[3];
          break;
        default:
          i = n;
          break;
      }
    }
    return path;
  }

  private static int needs(char command) {
    switch (command) {
      case 'M':
      case 'm':
      case 'L':
      case 'l':
        return 2;
      case 'H':
      case 'h':
      case 'V':
      case 'v':
        return 1;
      case 'C':
      case 'c':
        return 6;
      case 'Q':
      case 'q':
        return 4;
      default:
        return 0;
    }
  }

  /**
   * A ring with a gap, used by the gizmo's pan band and by latched indicators. Angles are degrees,
   * clockwise from 3 o'clock, matching Canvas.
   */
  static void arc(Canvas canvas, float cx, float cy, float radius, float startDeg, float sweepDeg,
                  int color, float widthPx) {
    canvas.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
        startDeg, sweepDeg, false, strokePaint(color, widthPx));
  }
}
