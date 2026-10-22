  private void drnasMouseButtonSizes(int percent, boolean save) {
    drnasMouseButtonPercent = Math.max(90, Math.min(150, percent));
    final float factor = drnasMouseButtonPercent / 100f;
    if (drnasMouseDockView != null) {
      final int diameter = dpDrnas(Math.max(156f, 184f * factor));
      drnasMouseDockView.setLayoutParams(new LinearLayout.LayoutParams(diameter, diameter));
      drnasMouseDockView.requestLayout();
    }
    if (save && drnasPrefs != null) {
      drnasPrefs.edit().putInt("mouse_button_percent", drnasMouseButtonPercent).apply();
    }
    drnasRefreshPanels();
  }
