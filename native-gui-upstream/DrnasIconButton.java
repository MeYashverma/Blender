package org.blender.blender;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.Button;

/** Compact icon-first shortcut cell with Android Button input and accessibility semantics. */
public class DrnasIconButton extends Button {

  private String iconName;
  private int iconTint = DrnasSkin.ICON;
  private float iconDp = DrnasSkin.ICON_MD;

  public DrnasIconButton(Context context) {
    super(context);
    init();
  }

  public DrnasIconButton(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public DrnasIconButton(Context context, AttributeSet attrs, int style) {
    super(context, attrs, style);
    init();
  }

  private void init() {
    setAllCaps(false);
    setSingleLine(true);
    setIncludeFontPadding(false);
    setMinWidth(0);
    setMinimumWidth(0);
    setMinHeight(0);
    setMinimumHeight(0);
  }

  public void setDrnasIcon(String name, float sizeDp) {
    if (name != null && !DrnasIcons.has(name)) {
      throw new IllegalArgumentException("Unknown DRNAS vector icon: " + name);
    }
    iconName = name;
    iconDp = Math.max(12f, Math.min(32f, sizeDp));
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) {
    if (iconName == null) {
      super.onDraw(canvas);
      return;
    }
    final CharSequence label = getText();
    final float alpha = isEnabled() ? 1f : DrnasSkin.ALPHA_DISABLED;
    final int tint = isActivated() ? DrnasSkin.ACCENT_BRIGHT : iconTint;
    if (label == null || label.length() == 0) {
      DrnasChrome.icon(canvas, iconName, getWidth() / 2f, getHeight() / 2f,
          DrnasChrome.dp(this, iconDp), tint, DrnasSkin.ICON_STROKE, alpha);
      return;
    }
    // Icon + compact caption; readable at a glance without a wall of Android-style text buttons.
    final float centerY = getHeight() * 0.34f;
    DrnasChrome.icon(canvas, iconName, getWidth() / 2f, centerY,
        DrnasChrome.dp(this, iconDp), tint, DrnasSkin.ICON_STROKE, alpha);
    final float captionSize = DrnasChrome.dp(this, Math.min(10f,
        getTextSize() / getResources().getDisplayMetrics().density));
    final float width = DrnasChrome.measure(label.toString(), captionSize, true);
    DrnasChrome.text(canvas, label.toString(), (getWidth() - width) / 2f,
        getHeight() * 0.92f, captionSize, DrnasSkin.TEXT_SECONDARY, true);
  }
}
