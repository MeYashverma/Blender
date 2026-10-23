  private DrnasMouseSurfaceView drnasMouseSurfaceView;
  private LinearLayout drnasMouseButtonRow;
  private final java.util.ArrayList<DrnasMouseButtonView> drnasMouseButtonViews =
      new java.util.ArrayList<>();

  /** Installs a touchpad, explicit wheel rail and three independent mouse buttons. */
  private void drnasDockMouseButtons(LinearLayout dock) {
    for (String key : new String[] {"lmb", "mmb", "rmb"}) {
      final TextView oldButton = drnasResizeTargets.remove(key);
      if (oldButton == null) continue;
      final DrnasPanel oldPanel = drnasPanels.remove(oldButton);
      if (oldPanel != null) oldPanel.popup.dismiss();
      if (oldButton.getParent() instanceof ViewGroup) {
        ((ViewGroup)oldButton.getParent()).removeView(oldButton);
      }
    }

    dock.setOrientation(LinearLayout.VERTICAL);
    dock.setGravity(Gravity.CENTER_HORIZONTAL);
    dock.setClipChildren(false);
    dock.setClipToPadding(false);
    final View grip = dock.getChildCount() > 0 ? dock.getChildAt(0) : null;
    drnasDockGrip = grip;
    if (grip != null) {
      grip.setVisibility(drnasEdit ? View.VISIBLE : View.GONE);
      drnasEnableDrag(grip, dock, "zoom");
    }

    drnasMouseSurfaceView = new DrnasMouseSurfaceView(this);
    drnasMouseSurfaceView.setHorizontalAvailable(drnasHasCapability(DRNAS_CAP_HSCROLL));
    drnasMouseSurfaceView.setListener(new DrnasMouseSurfaceView.Listener() {
      @Override public void pointer(float dx, float dy) { drnasMovePointerRelative(dx, dy); }
      @Override public void wheel(float vertical, float horizontal) {
        if (vertical != 0f) drnasSendInput(6, 0f, 0f, vertical);
        if (horizontal != 0f && drnasHasCapability(DRNAS_CAP_HSCROLL)) {
          drnasSendInput(12, 0f, 0f, horizontal);
        }
      }
      @Override public void click(int button) {
        if (drnasMouseButtonModel != null) drnasMouseButtonModel.click(button);
      }
      @Override public void interaction() { drnasWakeSkin(); }
    });
    dock.addView(drnasMouseSurfaceView,
        new LinearLayout.LayoutParams(dpDrnas(220), dpDrnas(96)));
    drnasDockedMouse.put(drnasMouseSurfaceView, dock);

    drnasMouseButtonRow = new LinearLayout(this);
    drnasMouseButtonRow.setOrientation(LinearLayout.HORIZONTAL);
    drnasMouseButtonRow.setGravity(Gravity.CENTER);
    drnasMouseButtonRow.setClipChildren(false);
    drnasMouseButtonRow.setClipToPadding(false);
    for (int bit : new int[] {DrnasMouseState.LEFT, DrnasMouseState.MIDDLE,
        DrnasMouseState.RIGHT}) {
      final DrnasMouseButtonView button = new DrnasMouseButtonView(
          this, bit, drnasMouseButtonModel);
      final boolean middle = bit == DrnasMouseState.MIDDLE;
      button.setEnabled(!middle || drnasHasCapability(DRNAS_CAP_MIDDLE));
      button.setOnClickListener(v -> drnasWakeSkin());
      final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          0, dpDrnas(54), 1f);
      params.setMargins(dpDrnas(2), dpDrnas(3), dpDrnas(2), dpDrnas(2));
      drnasMouseButtonRow.addView(button, params);
      drnasMouseButtonViews.add(button);
      drnasDockedMouse.put(button, dock);
    }
    dock.addView(drnasMouseButtonRow,
        new LinearLayout.LayoutParams(dpDrnas(220), dpDrnas(58)));
    drnasDockedMouse.put(drnasMouseButtonRow, dock);
    drnasResizeTargets.put("lmb", drnasMouseButtonViews.get(0));
    drnasResizeTargets.put("mmb", drnasMouseButtonViews.get(1));
    drnasResizeTargets.put("rmb", drnasMouseButtonViews.get(2));
  }
