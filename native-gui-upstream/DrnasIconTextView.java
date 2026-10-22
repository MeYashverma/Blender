package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

/** A TextView that keeps Android's reliable touch/focus semantics and draws a crisp local vector. */
public class DrnasIconTextView extends TextView {

  private String iconName;
  private float iconDp = DrnasSkin.ICON_MD;
  private boolean iconAboveText;
  private int iconTint = DrnasSkin.ICON;
  private float stateAlpha = 1f;

  public DrnasIconTextView(Context context) {
    super(context);
    init();
  }

  public DrnasIconTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public DrnasIconTextView(Context context, AttributeSet attrs, int style) {
    super(context, attrs, style);
    init();
  }

  private void init() {
    setGravity(Gravity.CENTER);
    setIncludeFontPadding(false);
    setFocusable(false);
    setMinWidth(0);
    setMinimumWidth(0);
    setMinHeight(0);
    setMinimumHeight(0);
  }

  /** Assign a vector icon. Setting {@code aboveText} allows a short, genuinely useful caption. */
  public void setDrnasIcon(String name, float sizeDp, int color, boolean aboveText) {
    if (name != null && !DrnasIcons.has(name)) {
      throw new IllegalArgumentException("Unknown DRNAS vector icon: " + name);
    }
    iconName = name;
    iconDp = Math.max(12f, Math.min(32f, sizeDp));
    iconTint = color;
    iconAboveText = aboveText;
    invalidate();
  }

  public void setDrnasStateAlpha(float alpha) {
    stateAlpha = DrnasChrome.saturate(alpha);
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    if (iconName == null) {
      super.onDraw(canvas);
      return;
    }
    final CharSequence label = getText();
    if (!iconAboveText || label == null || label.length() == 0) {
      // Icon-only controls retain a generous hit target while the glyph stays visually compact.
      DrnasChrome.icon(canvas, iconName, getWidth() / 2f, getHeight() / 2f,
          DrnasChrome.dp(this, iconDp), iconTint, DrnasSkin.ICON_STROKE, stateAlpha);
      return;
    }
    final float iconPx = DrnasChrome.dp(this, iconDp);
    final float textBaseline = getHeight() * 0.79f;
    final float centerY = getHeight() * 0.34f;
    DrnasChrome.icon(canvas, iconName, getWidth() / 2f, centerY, iconPx,
        iconTint, DrnasSkin.ICON_STROKE, stateAlpha);
    // The ordinary TextView paints the caption with a clear color so Android still measures and
    // provides the accessible label. The visible label is painted here in the shared type ramp.
    final float textSize = DrnasChrome.dp(this, getTextSize() / getResources().getDisplayMetrics().density);
    final float width = DrnasChrome.measure(label.toString(), textSize, true);
    DrnasChrome.text(canvas, label.toString(), (getWidth() - width) / 2f, textBaseline,
        textSize, DrnasSkin.TEXT_SECONDARY, true);
  }
}
