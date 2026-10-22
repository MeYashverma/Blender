  private DrnasMouseDockView drnasMouseDockView;

  /** Installs one contiguous touch surface instead of three detached button cards and wheel keys. */
  private void drnasDockMouseButtons(LinearLayout dock) {
    // Remove any legacy per-button panels before adding the replacement dock.
    for (String key : new String[] {"lmb", "mmb", "rmb"}) {
      final TextView oldButton = drnasResizeTargets.remove(key);
      if (oldButton == null) continue;
      final DrnasPanel oldPanel = drnasPanels.remove(oldButton);
      if (oldPanel != null) oldPanel.popup.dismiss();
      if (oldButton.getParent() instanceof ViewGroup) {
        ((ViewGroup)oldButton.getParent()).removeView(oldButton);
      }
    }

    drnasMouseDockView = new DrnasMouseDockView(this);
    drnasMouseDockView.setMiddleAvailable(drnasHasCapability(DRNAS_CAP_MIDDLE));
    drnasMouseDockView.setHorizontalAvailable(drnasHasCapability(DRNAS_CAP_HSCROLL));
    drnasMouseDockView.setListener(new DrnasMouseDockView.Listener() {
      @Override public void button(int bit, boolean down) {
        if (drnasMouseButtons == null) return;
        if (down) {
          if (drnasMouseButtons.press(bit)) drnasHaptic(drnasMouseDockView);
        } else {
          drnasMouseButtons.release(bit);
        }
        drnasWakeSkin();
      }

      @Override public void wheel(float vertical, float horizontal) {
        drnasWakeSkin();
        if (vertical != 0f) drnasSendInput(6, 0f, 0f, vertical);
        if (horizontal != 0f && drnasHasCapability(DRNAS_CAP_HSCROLL)) {
          drnasSendInput(12, 0f, 0f, horizontal);
        }
      }
    });
    final LinearLayout.LayoutParams dockSize =
        new LinearLayout.LayoutParams(dpDrnas(174), dpDrnas(116));
    dockSize.setMargins(dpDrnas(4), 0, 0, 0);
    dock.addView(drnasMouseDockView, dockSize);
    drnasResizeTargets.put("lmb", drnasMouseDockView);
    drnasResizeTargets.put("mmb", drnasMouseDockView);
    drnasResizeTargets.put("rmb", drnasMouseDockView);
    drnasDockedMouse.put(drnasMouseDockView, dock);
  }
