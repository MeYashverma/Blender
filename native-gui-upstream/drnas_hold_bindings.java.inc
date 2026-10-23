  private final java.util.Map<View, DrnasInputHold> drnasInputHolds = new java.util.LinkedHashMap<>();
  private final java.util.Map<View, Runnable> drnasInputResets = new java.util.LinkedHashMap<>();

  private void drnasReleaseAllInputs() {
    for (Runnable reset : drnasInputResets.values()) reset.run();
    for (java.util.Map.Entry<View, DrnasInputHold> entry : drnasInputHolds.entrySet()) {
      entry.getValue().cancel();
      entry.getKey().setPressed(false); entry.getKey().setActivated(false);
      if (entry.getKey() instanceof TextView) {
        TextView text = (TextView)entry.getKey();
        text.setText(text.getText().toString().replace(" •", ""));
      }
    }
  }

  private void drnasBindInput(TextView button, int key, int mouseDown, int mouseUp) {
    final CharSequence visibleLabel = button.getText();
    final CharSequence accessibleLabel = button.getContentDescription();
    final boolean iconOnly = visibleLabel == null || visibleLabel.length() == 0;
    final String label = (iconOnly
        ? accessibleLabel == null ? "" : accessibleLabel.toString()
        : visibleLabel.toString()).replace(" •", "");
    final DrnasInputHold hold = new DrnasInputHold(pressed -> {
      if (mouseDown != 0) drnasSendInput(pressed ? mouseDown : mouseUp, 0, 0, pressed ? 1 : 0);
      else {
        int modifier = key == KeyEvent.KEYCODE_SHIFT_LEFT ? KeyEvent.META_SHIFT_ON
            : key == KeyEvent.KEYCODE_CTRL_LEFT ? KeyEvent.META_CTRL_ON
            : key == KeyEvent.KEYCODE_ALT_LEFT ? KeyEvent.META_ALT_ON : 0;
        if (pressed) drnasStickyModifiers |= modifier;
        else drnasStickyModifiers &= ~modifier;
        nativeOnKey(key, pressed ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, drnasStickyModifiers);
      }
    }, android.view.ViewConfiguration.getDoubleTapTimeout());
    drnasInputHolds.put(button, hold);
    final float[] origin = new float[2];
    final boolean[] moved = new boolean[1];
    final boolean[] touching = new boolean[1], deferred = new boolean[1];
    final long[] downAt = new long[1];
    final boolean immediate = mouseDown != 0 || key == KeyEvent.KEYCODE_SHIFT_LEFT
        || key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_ALT_LEFT;
    drnasInputResets.put(button, () -> { touching[0] = deferred[0] = false; moved[0] = true; });
    final int slop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();
    button.setOnTouchListener((v, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          drnasWakeSkin();
          origin[0] = event.getX(); origin[1] = event.getY(); moved[0] = false;
          touching[0] = true; downAt[0] = event.getEventTime();
          if (mouseDown != 0) v.getParent().requestDisallowInterceptTouchEvent(true);
          // Command keys still activate on UP: swiping the horizontal strip
          // must not accidentally start Move/Rotate/Delete while scrolling.
          deferred[0] = !immediate && !hold.isLatched();
          if (!deferred[0]) hold.down(event.getEventTime());
          break;
        case MotionEvent.ACTION_MOVE:
          if (Math.abs(event.getX() - origin[0]) > slop || Math.abs(event.getY() - origin[1]) > slop) moved[0] = true;
          break;
        case MotionEvent.ACTION_UP:
          touching[0] = false;
          if (deferred[0] && !moved[0]) hold.down(downAt[0]);
          if (deferred[0] && moved[0]) hold.cancel();
          else hold.up(event.getEventTime(), !moved[0]);
          deferred[0] = false;
          if (mouseDown != 0) v.getParent().requestDisallowInterceptTouchEvent(false);
          break;
        case MotionEvent.ACTION_CANCEL:
          touching[0] = deferred[0] = false;
          hold.cancel();
          if (mouseDown != 0) v.getParent().requestDisallowInterceptTouchEvent(false);
          break;
        default: return true;
      }
      button.setPressed(touching[0] && !hold.isLatched());
      button.setActivated(hold.isHeld());
      final String nextLabel = iconOnly ? "" : label + (hold.isLatched() ? " •" : "");
      boolean changed = !button.getText().toString().equals(nextLabel);
      button.setText(nextLabel);
      button.setContentDescription(label + (hold.isLatched() ? " — zaključano; dodirni za otključavanje" : " — dvostruki dodir za zaključavanje"));
      if (changed && mouseDown == 0) drnasButtonSizes(drnasButtonPercent, false);
      return true;
    });
    button.setOnClickListener(v -> {
      if (hold.isLatched()) hold.cancel();
      else { hold.down(android.os.SystemClock.uptimeMillis()); hold.up(android.os.SystemClock.uptimeMillis(), false); }
      button.setText(iconOnly ? "" : label); button.setPressed(false); button.setActivated(false);
    });
  }

  private int drnasMouseButtonPercent = 100;
  private View drnasMouseDock, drnasDockGrip, drnasDockZoomToggle, drnasDockZoomSlider;
  private void drnasMouseButtonSizes(int percent, boolean save) {
    drnasMouseButtonPercent = Math.max(60, Math.min(150, percent));
    float factor = drnasMouseButtonPercent / 100f;
    for (java.util.Map.Entry<String, TextView> entry : drnasResizeTargets.entrySet()) {
      final String key = entry.getKey();
      final TextView button = entry.getValue();
      final boolean middle = key.equals("mmb");
      final boolean wheel = key.startsWith("wheel") || key.startsWith("hleft") || key.startsWith("hright");
      final float target = middle ? DrnasSkin.DOCK_TARGET_CENTRE
          : wheel ? DrnasSkin.TOUCH_MIN : DrnasSkin.DOCK_TARGET;
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
          dpDrnas(Math.max(DrnasSkin.TOUCH_MIN, target * factor)),
          dpDrnas(Math.max(DrnasSkin.TOUCH_MIN, target * factor)));
      lp.setMargins(dpDrnas(wheel ? 0 : 2), dpDrnas(2), dpDrnas(wheel ? 0 : 2), dpDrnas(2));
      button.setLayoutParams(lp);
      if (!(button instanceof DrnasIconTextView)) button.setTextSize(14f * factor);
    }
    if (drnasDockGrip != null) {
      drnasDockGrip.setLayoutParams(new LinearLayout.LayoutParams(dpDrnas(30 * factor), dpDrnas(48 * factor)));
      ((TextView)drnasDockGrip).setTextSize(16 * factor);
    }
    if (drnasDockZoomToggle != null) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpDrnas(48), dpDrnas(48));
      lp.setMargins(dpDrnas(1), 0, dpDrnas(1), 0);
      drnasDockZoomToggle.setLayoutParams(lp); ((TextView)drnasDockZoomToggle).setTextSize(11f * factor);
    }
    if (save) drnasPrefs.edit().putInt("mouse_button_percent", drnasMouseButtonPercent).apply();
    drnasRefreshPanels();
  }

  private void drnasInstallMouseSizeOptions(LinearLayout options) {
    options.addView(drnasUiLabel("VELIČINA MIŠA I TOUCHPADA"));
    final TextView value = drnasUiLabel(drnasMouseButtonPercent + "% • LMB / MMB / RMB · scroll");
    options.addView(value);
    SeekBar size = new SeekBar(this); drnasStyleSeekBar(size); size.setMax(60); size.setProgress(drnasMouseButtonPercent - 90);
    options.addView(size, new LinearLayout.LayoutParams(-1, dpDrnas(42)));
    size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar b, int progress, boolean user) {
        drnasMouseButtonSizes(progress + 90, user);
        value.setText(drnasMouseButtonPercent + "% • LMB / MMB / RMB · scroll");
      }
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) {}
    });
    options.addView(drnasUiLabel("Dvostruki dodir: zaključaj tipku • jedan dodir: otključaj"));
  }
