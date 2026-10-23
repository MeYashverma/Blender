package org.blender.blender;

/**
 * The single visual language for the DRNAS control layer.
 *
 * <p>One place defines every colour, radius, spacing step, icon size, alpha state and animation
 * duration. Views never invent their own values, which is what made the first overlay look like a
 * debug HUD: three different corner radii, platform buttons next to hand-drawn pills, and text
 * labels standing in for icons.
 *
 * <p>Deliberately Android-free (ints and floats only), so the palette can be unit tested: the tests
 * assert contrast ratios, that every alpha state stays readable, and that the geometry stays on the
 * 4 dp grid. The same numbers drive the offline preview renderer, so a design change is visible
 * without building an APK.
 *
 * <p>Direction: Blender's own dark neutral surfaces (a touch warm, never blue-black), one restrained
 * accent used only for active/pressed state, hairline separation instead of shadows, and muted
 * desaturated axis colours that read over a rendered viewport.
 */
public final class DrnasSkin {

  private DrnasSkin() {
  }

  /* ------------------------------------------------------------------ surfaces */

  /** Floating surface behind a control group. 88% opaque so the viewport still reads through. */
  public static final int SURFACE = 0xE01C1F24;
  /** Slightly lifted surface for primary mouse-pad and button targets. */
  public static final int SURFACE_RAISED = 0xE6282D34;
  /** Pressed / hovered fill inside a surface. */
  public static final int SURFACE_PRESSED = 0x33FFFFFF;
  /** Even rows and inset wells (slider tracks, segmented backplate). */
  public static final int WELL = 0x40000000;
  /** Hairline separation between a group and the viewport. */
  public static final int HAIRLINE = 0x1FFFFFFF;
  /** Stronger hairline: group boundaries, segmented dividers. */
  public static final int HAIRLINE_STRONG = 0x33FFFFFF;

  /** Dim scrim behind a modal sheet; keeps the viewport visible but recedes. */
  public static final int SCRIM = 0x66000000;

  /* ------------------------------------------------------------------ content */

  public static final int ICON = 0xE6E9EEF5;
  public static final int ICON_SECONDARY = 0xFF9AA3AE;
  public static final int ICON_DISABLED = 0x669AA3AE;
  public static final int TEXT = 0xFFE6E9EE;
  public static final int TEXT_SECONDARY = 0xFFA8B0BA;
  public static final int TEXT_DISABLED = 0x66A8B0BA;

  /* ------------------------------------------------------------------ accent */

  /** Blender's selection blue, used only for active state - never for decoration. */
  public static final int ACCENT = 0xFF4772B3;
  public static final int ACCENT_BRIGHT = 0xFF8FB4E8;
  /** Low-alpha accent wash behind a latched control. */
  public static final int ACCENT_WASH = 0x384772B3;
  /** Destructive/cancel affordance (Esc, Delete). Used sparingly. */
  public static final int DANGER = 0xFFD06A5C;

  /** Desaturated axis colours: readable over a render, still conventional. */
  public static final int AXIS_X = 0xFFC65C52;
  public static final int AXIS_Y = 0xFF70B260;
  public static final int AXIS_Z = 0xFF5884C4;

  /* ------------------------------------------------------------------ alpha states */

  /** Nothing touched recently: still fully legible, just less assertive. */
  public static final float ALPHA_ACTIVE = 1.0f;
  public static final float ALPHA_IDLE = 0.86f;
  /** Collapsed to a handle; must remain discoverable. */
  public static final float ALPHA_MINIMIZED = 0.58f;
  /** A control the running library cannot support stays visible but visibly inert. */
  public static final float ALPHA_DISABLED = 0.38f;

  /** Idle fade starts after this long without interaction. */
  public static final long IDLE_AFTER_MS = 6000L;

  /* ------------------------------------------------------------------ geometry (dp) */

  public static final float RADIUS_GROUP = 20f;
  public static final float RADIUS_ITEM = 13f;
  public static final float RADIUS_SMALL = 9f;
  public static final float RADIUS_PILL = 999f;

  public static final float SPACE_XS = 4f;
  public static final float SPACE_S = 6f;
  public static final float SPACE_M = 10f;
  public static final float SPACE_L = 14f;

  public static final float ICON_SM = 16f;
  public static final float ICON_MD = 20f;
  public static final float ICON_LG = 24f;

  /** Minimum clickable mouse button height at the smallest supported controller scale. */
  public static final float MOUSE_BUTTON_MIN = 48f;
  /** Mouse buttons remain substantial targets even when the drawing is compact. */
  public static final float MOUSE_BUTTON_TARGET = 54f;
  /** Minimum touch diameter, independent of how small the icon looks. */
  public static final float TOUCH_MIN = 48f;

  /** Toolbar cell: compact visually, generous to touch. */
  public static final float TOOL_CELL = 42f;
  public static final float TOOL_CELL_TOUCH = 48f;
  public static final float TOOLBAR_HEIGHT = 52f;

  public static final float HAIRLINE_WIDTH = 1f;
  public static final float ICON_STROKE = 1.7f;

  /* ------------------------------------------------------------------ animation */

  /** Press feedback: fast enough to feel like part of the input, not a transition. */
  public static final long PRESS_MS = 120L;
  /** Panel expand / collapse / minimise. */
  public static final long PANEL_MS = 160L;
  /** Idle opacity change. */
  public static final long FADE_MS = 180L;
  /** Pressed scale, so a tap reads as physical without looking bouncy. */
  public static final float PRESS_SCALE = 0.94f;

  /* ------------------------------------------------------------------ helpers */

  /** Multiplies the alpha channel of an ARGB colour, leaving RGB untouched. */
  public static int withAlphaScale(int color, float scale) {
    if (scale < 0f) {
      scale = 0f;
    }
    if (scale > 1f) {
      scale = 1f;
    }
    final int alpha = Math.round(((color >>> 24) & 0xFF) * scale);
    return (alpha << 24) | (color & 0x00FFFFFF);
  }

  /** Alpha value of a colour as 0..1. */
  public static float alphaOf(int color) {
    return ((color >>> 24) & 0xFF) / 255f;
  }

  /** Single highlight strength used for pressed states, so every control feels the same. */
  public static int pressedFill(int base) {
    return blend(base, 0xFFFFFFFF, 0.10f);
  }

  /** Linear blend of two ARGB colours, {@code t} from 0 (a) to 1 (b). */
  public static int blend(int a, int b, float t) {
    if (t <= 0f) {
      return a;
    }
    if (t >= 1f) {
      return b;
    }
    final int aa = (a >>> 24) & 0xFF;
    final int ar = (a >> 16) & 0xFF;
    final int ag = (a >> 8) & 0xFF;
    final int ab = a & 0xFF;
    final int ba = (b >>> 24) & 0xFF;
    final int br = (b >> 16) & 0xFF;
    final int bg = (b >> 8) & 0xFF;
    final int bb = b & 0xFF;
    final int ca = Math.round(aa + (ba - aa) * t);
    final int cr = Math.round(ar + (br - ar) * t);
    final int cg = Math.round(ag + (bg - ag) * t);
    final int cb = Math.round(ab + (bb - ab) * t);
    return (ca << 24) | (cr << 16) | (cg << 8) | cb;
  }

  /**
   * WCAG relative luminance of the opaque part of a colour. Used by the tests to prove that icons
   * stay readable on every surface the layer draws, in every alpha state.
   */
  public static float luminance(int color) {
    return channel((color >> 16) & 0xFF) * 0.2126f
        + channel((color >> 8) & 0xFF) * 0.7152f
        + channel(color & 0xFF) * 0.0722f;
  }

  private static float channel(int value) {
    final float c = value / 255f;
    return c <= 0.03928f ? c / 12.92f : (float) Math.pow((c + 0.055) / 1.055, 2.4);
  }

  /** WCAG contrast ratio between two colours (1..21), ignoring their alpha. */
  public static float contrast(int a, int b) {
    final float la = luminance(a);
    final float lb = luminance(b);
    final float hi = Math.max(la, lb);
    final float lo = Math.min(la, lb);
    return (hi + 0.05f) / (lo + 0.05f);
  }

  /**
   * Composites a translucent colour over an opaque backdrop, which is what the eye sees when a
   * panel floats over the viewport. Tests use this to check real contrast rather than nominal
   * alpha.
   */
  public static int over(int color, int backdrop) {
    final float a = alphaOf(color);
    return blend(backdrop, (0xFF << 24) | (color & 0x00FFFFFF), a);
  }

  /** Rounds to the 4 dp grid so spacing stays visually consistent. */
  public static float snap(float value) {
    return Math.round(value / 4f) * 4f;
  }

  /** Idle opacity for a "resting" group, honouring a user opacity preference (0..100). */
  public static float groupAlpha(boolean active, int opacityPercent) {
    final float base = active ? ALPHA_ACTIVE : ALPHA_IDLE;
    final float user = Math.max(0f, Math.min(1f, opacityPercent / 100f));
    return base * (0.45f + 0.55f * user);
  }
}
