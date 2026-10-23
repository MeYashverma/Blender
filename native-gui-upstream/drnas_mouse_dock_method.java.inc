  private DrnasMouseRingView drnasMouseDockView;

  /** Installs the circular, touch-first mouse controller used by the landscape layout. */
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

    drnasMouseDockView = new DrnasMouseRingView(this);
    drnasMouseDockView.setMiddleAvailable(drnasHasCapability(DRNAS_CAP_MIDDLE));
    drnasMouseDockView.setHorizontalAvailable(drnasHasCapability(DRNAS_CAP_HSCROLL));
    drnasMouseDockView.setListener(new DrnasMouseRingView.Listener() {
      @Override public void button(int bit, boolean down) {
        if (drnasMouseButtons == null) return;
        if (down) {
          if (drnasMouseButtons.press(bit)) drnasHaptic(drnasMouseDockView);
        } else {
          drnasMouseButtons.release(bit);
        }
      }

      @Override public void wheel(float vertical, float horizontal) {
        if (vertical != 0f) drnasSendInput(6, 0f, 0f, vertical);
        if (horizontal != 0f && drnasHasCapability(DRNAS_CAP_HSCROLL)) {
          drnasSendInput(12, 0f, 0f, horizontal);
        }
      }

      @Override public void interaction() { drnasWakeSkin(); }
    });
    dock.addView(drnasMouseDockView,
        new LinearLayout.LayoutParams(dpDrnas(184), dpDrnas(184)));
    drnasResizeTargets.put("lmb", drnasMouseDockView);
    drnasResizeTargets.put("mmb", drnasMouseDockView);
    drnasResizeTargets.put("rmb", drnasMouseDockView);
    drnasDockedMouse.put(drnasMouseDockView, dock);
  }
