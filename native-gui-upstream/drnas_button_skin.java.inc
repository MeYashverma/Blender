  private android.graphics.drawable.GradientDrawable drnasKeySurface(boolean active, boolean mouse) {
    android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
    shape.setColor(active ? DrnasSkin.SURFACE_PRESSED : DrnasSkin.SURFACE);
    shape.setCornerRadius(dpDrnas(mouse ? DrnasSkin.RADIUS_ITEM : DrnasSkin.RADIUS_SMALL));
    shape.setStroke(dpDrnas(1), active ? DrnasSkin.ACCENT : DrnasSkin.HAIRLINE);
    return shape;
  }
  private void drnasStyleKey(TextView button, boolean mouse) {
    android.animation.StateListAnimator stateAnimator = new android.animation.StateListAnimator();
    android.animation.ObjectAnimator pressedScaleX = android.animation.ObjectAnimator.ofFloat(button, View.SCALE_X, 1f, DrnasSkin.PRESS_SCALE);
    android.animation.ObjectAnimator pressedScaleY = android.animation.ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f, DrnasSkin.PRESS_SCALE);
    pressedScaleX.setDuration(DrnasSkin.PRESS_MS); pressedScaleY.setDuration(DrnasSkin.PRESS_MS);
    android.animation.AnimatorSet pressed = new android.animation.AnimatorSet();
    pressed.playTogether(pressedScaleX, pressedScaleY);
    stateAnimator.addState(new int[] {android.R.attr.state_pressed}, pressed);
    android.animation.ObjectAnimator normalX = android.animation.ObjectAnimator.ofFloat(button, View.SCALE_X, 1f);
    android.animation.ObjectAnimator normalY = android.animation.ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f);
    normalX.setDuration(DrnasSkin.PRESS_MS); normalY.setDuration(DrnasSkin.PRESS_MS);
    android.animation.AnimatorSet normal = new android.animation.AnimatorSet(); normal.playTogether(normalX, normalY);
    stateAnimator.addState(new int[] {}, normal);
    button.setStateListAnimator(stateAnimator);
    android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
    states.addState(new int[] {android.R.attr.state_pressed}, drnasKeySurface(true, mouse));
    states.addState(new int[] {android.R.attr.state_activated}, drnasKeySurface(true, mouse));
    states.addState(new int[] {}, drnasKeySurface(false, mouse));
    button.setBackground(new android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(DrnasSkin.ACCENT_WASH), states, null));
    button.setTextColor(DrnasSkin.TEXT);
    button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    button.setTextSize(mouse ? 12 : 11);
    button.setAllCaps(false);
    button.setSingleLine(true);
    button.setIncludeFontPadding(false);
    button.setGravity(Gravity.CENTER);
    button.setPadding(dpDrnas(DrnasSkin.SPACE_S), 0, dpDrnas(DrnasSkin.SPACE_S), 0);
    button.setElevation(0f);
  }
