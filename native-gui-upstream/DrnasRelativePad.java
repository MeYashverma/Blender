package org.blender.blender;

/**
 * Relative ("virtual touchpad") pointer motion.
 *
 * <p>Swiping moves a visible pointer instead of dragging whatever is under the finger. Three
 * properties matter on a phone:
 * <ul>
 *   <li><b>precision</b> – a slow 1px swipe must move the pointer by a fraction of a pixel that is
 *       remembered, so fine work near a vertex is possible. Positions are kept in floats all the
 *       way to the native conversion, so no sub-pixel travel is ever thrown away;</li>
 *   <li><b>reach</b> – a fast swipe must cross the whole display, and the pointer must be able to
 *       land exactly on x = 0, y = 0 and on the last addressable pixel;</li>
 *   <li><b>bounded</b> – motion is always clamped to the native surface, never beyond it.</li>
 * </ul>
 *
 * <p>Acceleration is a small, configurable gain, not a desktop-style curve: touchpad swipe
 * distance already scales naturally with how far the thumb travels.
 *
 * <p>Android-free and unit tested by {@code DrnasRelativePadTest}.
 */
public final class DrnasRelativePad {

  private float x;
  private float y;
  private float maxX = 1f;
  private float maxY = 1f;
  private float speed = 1f;
  private float acceleration = 0.35f;
  private float accelerationSpan = 28f;

  public void setBounds(float maximumX, float maximumY) {
    maxX = Math.max(0f, maximumX);
    maxY = Math.max(0f, maximumY);
    x = clamp(x, maxX);
    y = clamp(y, maxY);
  }

  public float maxX() {
    return maxX;
  }

  public float maxY() {
    return maxY;
  }

  /** Base gain, 0.25x .. 3x. */
  public void setSpeed(float multiplier) {
    speed = Math.max(0.25f, Math.min(3f, multiplier));
  }

  public float speed() {
    return speed;
  }

  /** Extra gain at full swipe speed, 0 .. 2. Zero disables acceleration. */
  public void setAcceleration(float extraGain) {
    acceleration = Math.max(0f, Math.min(2f, extraGain));
  }

  public float acceleration() {
    return acceleration;
  }

  public void setAccelerationSpan(float pixelsPerEvent) {
    accelerationSpan = Math.max(4f, Math.min(200f, pixelsPerEvent));
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public void setPosition(float valueX, float valueY) {
    x = clamp(valueX, maxX);
    y = clamp(valueY, maxY);
  }

  /** Centres the pointer, e.g. after a mode switch or a rotation. */
  public void centre() {
    x = maxX * 0.5f;
    y = maxY * 0.5f;
  }

  /** Gain actually applied to a swipe of {@code distance} px. Always >= 1 at speed 1. */
  public float gain(float distance) {
    if (acceleration <= 0f) {
      return speed;
    }
    final float ramp = Math.min(1f, Math.abs(distance) / accelerationSpan);
    return speed * (1f + acceleration * ramp);
  }

  /**
   * Applies one swipe delta. Returns true when the pointer moved.
   */
  public boolean move(float dx, float dy) {
    final float distance = (float) Math.hypot(dx, dy);
    final float gain = gain(distance);
    final float nextX = clamp(x + dx * gain, maxX);
    final float nextY = clamp(y + dy * gain, maxY);
    if (nextX == x && nextY == y) {
      return false;
    }
    x = nextX;
    y = nextY;
    return true;
  }

  private static float clamp(float value, float maximum) {
    if (value < 0f) {
      return 0f;
    }
    return value > maximum ? maximum : value;
  }
}
