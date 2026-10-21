package org.blender.blender;

/**
 * The one and only coordinate strategy for the DRNAS pointer.
 *
 * <p>Two spaces exist on screen and they are not the same rectangle:
 * <ul>
 *   <li><b>view space</b> – the Android window/popup that receives the finger. Its own
 *       {@code getWidth()/getHeight()} are in physical pixels, already density independent
 *       because Android reports window sizes in px.</li>
 *   <li><b>native space</b> – the Blender GHOST input surface (the activity decor view). This is
 *       what {@code nativeOnDrnasInput} must be given; GHOST scales it internally with
 *       {@code ghost_android_scale_input()}.</li>
 * </ul>
 *
 * <p>After stretch/rotation/IME changes the two rectangles regularly differ. Every conversion in
 * the overlay goes through this class so that a scale or translation can never be applied twice –
 * the bug class reported for stretched files-browser dialogs. The pointer is stored in
 * <em>native</em> space only; the cursor sprite and any popup placement are derived from it with
 * {@link #viewX(float)} / {@link #viewY(float)}, so what the user sees and what Blender receives
 * are necessarily the same point.
 *
 * <p>Pure Java on purpose: it is unit tested without an Android device (see
 * {@code DrnasPointerMapTest}).
 */
public final class DrnasPointerMap {

  /** View spans the whole native surface; only the aspect may differ (the usual popup case). */
  public static final int MODE_FIT = 0;
  /**
   * The view is a 1:1 window onto the native surface that starts at an offset (a pad that shrank
   * because the soft keyboard is up, a dialog-hosted pad, ...). No scaling is applied at all,
   * which is exactly what keeps a stretched display aligned.
   */
  public static final int MODE_SUBRECT = 1;

  private int nativeWidth = 1;
  private int nativeHeight = 1;
  private int viewWidth = 1;
  private int viewHeight = 1;
  private int viewOriginX;
  private int viewOriginY;
  private int mode = MODE_FIT;

  public void setNativeSize(int width, int height) {
    nativeWidth = Math.max(1, width);
    nativeHeight = Math.max(1, height);
  }

  public void setViewSize(int width, int height) {
    viewWidth = Math.max(1, width);
    viewHeight = Math.max(1, height);
  }

  public void setMode(int value) {
    mode = value == MODE_SUBRECT ? MODE_SUBRECT : MODE_FIT;
  }

  /** Offset of view (0,0) inside native space; only meaningful in {@link #MODE_SUBRECT}. */
  public void setViewOrigin(int x, int y) {
    viewOriginX = x;
    viewOriginY = y;
  }

  public int getMode() {
    return mode;
  }

  public int getNativeWidth() {
    return nativeWidth;
  }

  public int getNativeHeight() {
    return nativeHeight;
  }

  public int getViewWidth() {
    return viewWidth;
  }

  public int getViewHeight() {
    return viewHeight;
  }

  /** True once both rectangles are known, so callers can skip a frame instead of guessing. */
  public boolean isReady() {
    return nativeWidth > 1 && nativeHeight > 1 && viewWidth > 1 && viewHeight > 1;
  }

  /** Last addressable native pixel column; GHOST treats the surface as [0, width-1]. */
  public float maxNativeX() {
    return nativeWidth - 1f;
  }

  public float maxNativeY() {
    return nativeHeight - 1f;
  }

  public float maxViewX() {
    return viewWidth - 1f;
  }

  public float maxViewY() {
    return viewHeight - 1f;
  }

  private float clamp(float value, float max) {
    if (value < 0f) {
      return 0f;
    }
    return value > max ? max : value;
  }

  /** View x -> native x. Always clamped, so the pointer can reach every native edge. */
  public float nativeX(float x) {
    final float vx = clamp(x, maxViewX());
    if (mode == MODE_SUBRECT) {
      return clamp(viewOriginX + vx, maxNativeX());
    }
    return vx * maxNativeX() / maxViewX();
  }

  public float nativeY(float y) {
    final float vy = clamp(y, maxViewY());
    if (mode == MODE_SUBRECT) {
      return clamp(viewOriginY + vy, maxNativeY());
    }
    return vy * maxNativeY() / maxViewY();
  }

  /** Native x -> view x, for drawing the cursor where Blender actually points. */
  public float viewX(float x) {
    final float nx = clamp(x, maxNativeX());
    if (mode == MODE_SUBRECT) {
      return clamp(nx - viewOriginX, maxViewX());
    }
    return nx * maxViewX() / maxNativeX();
  }

  public float viewY(float y) {
    final float ny = clamp(y, maxNativeY());
    if (mode == MODE_SUBRECT) {
      return clamp(ny - viewOriginY, maxViewY());
    }
    return ny * maxViewY() / maxNativeY();
  }

  /**
   * Screen position of the cursor sprite: the decor origin (status bar, cutout, system gesture
   * inset) plus the mapped view point, minus half the sprite so the hotspot sits under the tip.
   */
  public int cursorLeft(float pointerNativeX, float spriteSize, int decorOnScreenX) {
    return Math.round(decorOnScreenX + viewX(pointerNativeX) - spriteSize * 0.25f);
  }

  public int cursorTop(float pointerNativeY, float spriteSize, int decorOnScreenY) {
    return Math.round(decorOnScreenY + viewY(pointerNativeY) - spriteSize * 0.25f);
  }
}
