  private void drnasMouseButtonSizes(int percent, boolean save) {
    drnasMouseButtonPercent = Math.max(90, Math.min(150, percent));
    final float factor = drnasMouseButtonPercent / 100f;
    if (drnasMouseSurfaceView != null) {
      drnasMouseSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(
          dpDrnas(220f * factor), dpDrnas(96f * factor)));
      drnasMouseSurfaceView.requestLayout();
    }
    if (drnasMouseButtonRow != null) {
      drnasMouseButtonRow.setLayoutParams(new LinearLayout.LayoutParams(
          dpDrnas(220f * factor), dpDrnas(58f * factor)));
      for (DrnasMouseButtonView button : drnasMouseButtonViews) {
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)button.getLayoutParams();
        params.height = dpDrnas(54f * factor);
        params.setMargins(dpDrnas(2f * factor), dpDrnas(3f * factor),
            dpDrnas(2f * factor), dpDrnas(2f * factor));
        button.setLayoutParams(params);
      }
    }
    if (drnasDockGrip != null) {
      drnasDockGrip.setLayoutParams(new LinearLayout.LayoutParams(
          dpDrnas(220f * factor), drnasEdit ? dpDrnas(24f * factor) : 0));
    }
    if (save && drnasPrefs != null) {
      drnasPrefs.edit().putInt("mouse_button_percent", drnasMouseButtonPercent).apply();
    }
    drnasRefreshPanels();
  }
