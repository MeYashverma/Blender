  private void drnasMouseButtonSizes(int percent, boolean save) {
    drnasMouseButtonPercent = Math.max(90, Math.min(150, percent));
    final float factor = drnasMouseButtonPercent / 100f;
    if (drnasMouseDockView != null) {
      final int width = dpDrnas(Math.max(156f, 174f * factor));
      final int height = dpDrnas(Math.max(116f, 116f * factor));
      final LinearLayout.LayoutParams dockSize = new LinearLayout.LayoutParams(width, height);
      dockSize.setMargins(dpDrnas(4), 0, 0, 0);
      drnasMouseDockView.setLayoutParams(dockSize);
      drnasMouseDockView.requestLayout();
    }
    if (save && drnasPrefs != null) {
      drnasPrefs.edit().putInt("mouse_button_percent", drnasMouseButtonPercent).apply();
    }
    drnasRefreshPanels();
  }
