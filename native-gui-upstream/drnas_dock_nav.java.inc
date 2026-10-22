  /* ==================================================================
   * DRNAS dock, navigation widget, keyboard extras and layout presets.
   * ================================================================== */

  private final android.os.Handler drnasVisualHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final java.util.ArrayList<View> drnasSkinGroups = new java.util.ArrayList<>();
  private final java.util.HashSet<View> drnasMinimizedGroups = new java.util.HashSet<>();
  private final Runnable drnasIdleFade = () -> {
    for (View group : drnasSkinGroups) {
      if (group != null && group.getVisibility() == View.VISIBLE) {
        final float alpha = drnasMinimizedGroups.contains(group)
            ? DrnasSkin.ALPHA_MINIMIZED : DrnasSkin.ALPHA_IDLE;
        group.animate().alpha(alpha).setDuration(DrnasSkin.FADE_MS).start();
      }
    }
  };

  private void drnasStyleSeekBar(SeekBar seek) {
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(DrnasSkin.ACCENT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(DrnasSkin.ACCENT_BRIGHT));
    seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(DrnasSkin.HAIRLINE_STRONG));
  }

  private void drnasStyleSwitch(android.widget.Switch toggle) {
    toggle.setTextColor(DrnasSkin.TEXT);
    toggle.setTextSize(12f);
    toggle.setPadding(dpDrnas(DrnasSkin.SPACE_S), dpDrnas(3), dpDrnas(DrnasSkin.SPACE_M), dpDrnas(3));
    final int[][] states = new int[][] {
        new int[] {android.R.attr.state_checked}, new int[] {}
    };
    toggle.setThumbTintList(new android.content.res.ColorStateList(states,
        new int[] {DrnasSkin.ACCENT_BRIGHT, DrnasSkin.ICON_SECONDARY}));
    toggle.setTrackTintList(new android.content.res.ColorStateList(states,
        new int[] {DrnasSkin.ACCENT_WASH, DrnasSkin.HAIRLINE_STRONG}));
  }

  private void drnasRegisterSkinGroup(View view) {
    if (view == null || drnasSkinGroups.contains(view)) return;
    drnasSkinGroups.add(view);
    view.setAlpha(DrnasSkin.ALPHA_IDLE);
  }

  private void drnasSetMinimized(View view, boolean minimized) {
    if (view == null) return;
    if (minimized) drnasMinimizedGroups.add(view);
    else drnasMinimizedGroups.remove(view);
    view.animate().alpha(minimized ? DrnasSkin.ALPHA_MINIMIZED : DrnasSkin.ALPHA_ACTIVE)
        .setDuration(DrnasSkin.PANEL_MS).start();
  }

  /** Fades back to readable-but-quiet after rest; never hides controls or delays input. */
  private void drnasWakeSkin() {
    drnasVisualHandler.removeCallbacks(drnasIdleFade);
    for (View group : drnasSkinGroups) {
      if (group != null && group.getVisibility() == View.VISIBLE) {
        group.animate().alpha(DrnasSkin.ALPHA_ACTIVE).setDuration(DrnasSkin.FADE_MS).start();
      }
    }
    drnasVisualHandler.postDelayed(drnasIdleFade, DrnasSkin.IDLE_AFTER_MS);
  }

  private final android.os.Handler drnasRepeatHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private Runnable drnasRepeatAction;

  private final Runnable drnasRepeatTicker = new Runnable() {
    public void run() {
      if (drnasRepeatAction == null) {
        return;
      }
      drnasRepeatAction.run();
      drnasRepeatHandler.postDelayed(this, 110);
    }
  };

  private void drnasStartRepeat(Runnable action) {
    drnasStopRepeat();
    drnasRepeatAction = action;
    drnasRepeatHandler.postDelayed(drnasRepeatTicker, 320);
  }

  private void drnasStopRepeat() {
    drnasRepeatAction = null;
    drnasRepeatHandler.removeCallbacks(drnasRepeatTicker);
  }

  /* ------------------------------------------------------------------ mouse dock */

  /** Wheel up/down (and horizontal) buttons: real wheel events, repeat while held. */
  /* ------------------------------------------------------------------ navigation + radial tool controls */

  private boolean drnasTyping() { return drnasImeVisible(); }

  private boolean drnasImeVisible() {
    try {
      if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow() != null) {
        final android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
      }
    } catch (Throwable ignored) {
      /* Gating must never break input on an older platform. */
    }
    return false;
  }

  private void drnasFlashDisabled(View view, String reason) {
    view.setAlpha(0.35f);
    view.postDelayed(() -> view.setAlpha(1f), 160);
    view.setContentDescription(reason);
  }

  private void installDrnasNavWidget() {
    final DrnasNavigationPadView nav = new DrnasNavigationPadView(this);
    nav.setCapabilities(drnasHasCapability(DRNAS_CAP_MIDDLE),
        drnasHasCapability(DRNAS_CAP_SHIFT), drnasHasCapability(DRNAS_CAP_KEYS));
    nav.setListener(new DrnasNavigationPadView.Listener() {
      private boolean active;
      private boolean panActive;

      @Override public void command(int command) {
        drnasWakeSkin();
        drnasHaptic(nav);
        if (command == DrnasNavigationPadView.ZOOM_IN) {
          drnasSendInput(6, 0f, 0f, 2f);
        } else if (command == DrnasNavigationPadView.ZOOM_OUT) {
          drnasSendInput(6, 0f, 0f, -2f);
        } else if (command == DrnasNavigationPadView.FRAME_SELECTED) {
          if (!drnasHasCapability(DRNAS_CAP_KEYS) || drnasTyping()) {
            drnasFlashDisabled(nav, "Frame requires native key input and no active text field");
          } else {
            drnasTap(android.view.KeyEvent.KEYCODE_NUMPAD_DOT, 0);
          }
        }
      }

      @Override public void begin(boolean pan) {
        if (!drnasHasCapability(DRNAS_CAP_MIDDLE) || drnasMouseButtons == null
            || drnasMouseButtons.isHeld(DrnasMouseState.MIDDLE)) return;
        if (pan && !drnasHasCapability(DRNAS_CAP_SHIFT)) return;
        drnasWakeSkin();
        drnasSyncPointerGeometry();
        if (pan) drnasSendInput(10, 0f, 0f, 1f);
        if (!drnasMouseButtons.press(DrnasMouseState.MIDDLE)) {
          if (pan) drnasSendInput(11, 0f, 0f, 0f);
          return;
        }
        active = true;
        panActive = pan;
      }

      @Override public void move(float dx, float dy, boolean pan) {
        if (!active) return;
        drnasPointerX = Math.max(0f, Math.min(drnasMap.maxNativeX(),
            drnasPointerX + dx * drnasNavSpeed));
        drnasPointerY = Math.max(0f, Math.min(drnasMap.maxNativeY(),
            drnasPointerY + dy * drnasNavSpeed));
        drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
      }

      @Override public void end(boolean pan) {
        if (!active) return;
        if (drnasMouseButtons != null) drnasMouseButtons.release(DrnasMouseState.MIDDLE);
        if (panActive) drnasSendInput(11, 0f, 0f, 0f);
        active = false;
        panActive = false;
      }

      @Override public void interaction() { drnasWakeSkin(); }
    });
    drnasNavBar = nav;
    drnasRegisterSkinGroup(nav);
    final FrameLayout.LayoutParams location = new FrameLayout.LayoutParams(
        dpDrnas(164), dpDrnas(164), Gravity.BOTTOM | Gravity.LEFT);
    location.leftMargin = dpDrnas(12);
    location.bottomMargin = dpDrnas(16);
    drnasAddPanel(nav, location);
    drnasSetOffsetX(nav, drnasPrefs.getFloat("nav_x", 0f));
    drnasSetOffsetY(nav, drnasPrefs.getFloat("nav_y", 0f));
    drnasEnableDrag(nav, nav, "nav");
    drnasPanelVisibility(nav,
        drnasPrefs.getBoolean("nav_visible", true) ? View.VISIBLE : View.GONE);
  }

  private DrnasRadialPaletteView drnasToolPaletteView;
  private int drnasToolPalettePercent = 100;

  private void drnasToolPaletteSize(int percent, boolean save) {
    drnasToolPalettePercent = Math.max(90, Math.min(150, percent));
    if (drnasToolPaletteView != null) {
      final int diameter = dpDrnas(Math.max(176f, 196f * drnasToolPalettePercent / 100f));
      drnasToolPaletteView.setLayoutParams(new FrameLayout.LayoutParams(diameter, diameter));
      drnasToolPaletteView.requestLayout();
    }
    if (save && drnasPrefs != null) {
      drnasPrefs.edit().putInt("tool_button_percent", drnasToolPalettePercent).apply();
    }
    drnasRefreshPanels();
  }

  private void installDrnasToolPalette() {
    final DrnasRadialPaletteView palette = new DrnasRadialPaletteView(this);
    palette.setPage(drnasPrefs.getInt("tool_page", DrnasRadialPaletteModel.PAGE_OBJECT));
    palette.setListener(new DrnasRadialPaletteView.Listener() {
      @Override public void action(int actionId) { drnasRunPaletteAction(palette, actionId); }
      @Override public void pageChanged(int page) {
        drnasPrefs.edit().putInt("tool_page", page).apply();
        drnasWakeSkin();
      }
      @Override public void interaction() { drnasWakeSkin(); }
    });
    drnasToolPaletteView = palette;
    drnasRegisterSkinGroup(palette);
    final FrameLayout.LayoutParams location = new FrameLayout.LayoutParams(
        dpDrnas(196), dpDrnas(196), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    location.bottomMargin = dpDrnas(18);
    drnasAddPanel(palette, location);
    drnasSetOffsetX(palette, drnasPrefs.getFloat("tools_x", 0f));
    drnasSetOffsetY(palette, drnasPrefs.getFloat("tools_y", 0f));
    drnasEnableDrag(palette, palette, "tools");
    drnasToolPaletteSize(drnasPrefs.getInt("tool_button_percent", 100), false);
    drnasPanelVisibility(palette,
        drnasPrefs.getBoolean("tools_visible", true) ? View.VISIBLE : View.GONE);
  }

  private void drnasRunPaletteAction(View source, int actionId) {
    if (drnasTyping()) {
      drnasFlashDisabled(source, "Tool commands are paused while editing text");
      return;
    }
    if (!drnasHasCapability(DRNAS_CAP_KEYS)) {
      drnasFlashDisabled(source, "This native build does not support shortcut input");
      return;
    }
    final int ctrl = android.view.KeyEvent.META_CTRL_ON;
    final int shift = android.view.KeyEvent.META_SHIFT_ON;
    final int key;
    final int meta;
    switch (actionId) {
      case DrnasRadialPaletteModel.SELECT: key = android.view.KeyEvent.KEYCODE_B; meta = 0; break;
      case DrnasRadialPaletteModel.MOVE: key = android.view.KeyEvent.KEYCODE_G; meta = 0; break;
      case DrnasRadialPaletteModel.ROTATE: key = android.view.KeyEvent.KEYCODE_R; meta = 0; break;
      case DrnasRadialPaletteModel.SCALE: key = android.view.KeyEvent.KEYCODE_S; meta = 0; break;
      case DrnasRadialPaletteModel.DUPLICATE: key = android.view.KeyEvent.KEYCODE_D; meta = shift; break;
      case DrnasRadialPaletteModel.DELETE: key = android.view.KeyEvent.KEYCODE_X; meta = 0; break;
      case DrnasRadialPaletteModel.UNDO: key = android.view.KeyEvent.KEYCODE_Z; meta = ctrl; break;
      case DrnasRadialPaletteModel.REDO: key = android.view.KeyEvent.KEYCODE_Z; meta = ctrl | shift; break;
      case DrnasRadialPaletteModel.EXTRUDE: key = android.view.KeyEvent.KEYCODE_E; meta = 0; break;
      case DrnasRadialPaletteModel.INSET: key = android.view.KeyEvent.KEYCODE_I; meta = 0; break;
      case DrnasRadialPaletteModel.BEVEL: key = android.view.KeyEvent.KEYCODE_B; meta = ctrl; break;
      case DrnasRadialPaletteModel.LOOP_CUT: key = android.view.KeyEvent.KEYCODE_R; meta = ctrl; break;
      case DrnasRadialPaletteModel.PLAY_PAUSE: key = android.view.KeyEvent.KEYCODE_SPACE; meta = 0; break;
      case DrnasRadialPaletteModel.PREVIOUS_FRAME: key = android.view.KeyEvent.KEYCODE_DPAD_LEFT; meta = 0; break;
      case DrnasRadialPaletteModel.NEXT_FRAME: key = android.view.KeyEvent.KEYCODE_DPAD_RIGHT; meta = 0; break;
      case DrnasRadialPaletteModel.INSERT_KEY: key = android.view.KeyEvent.KEYCODE_I; meta = 0; break;
      case DrnasRadialPaletteModel.FIRST_FRAME: key = android.view.KeyEvent.KEYCODE_DPAD_LEFT; meta = shift; break;
      case DrnasRadialPaletteModel.LAST_FRAME: key = android.view.KeyEvent.KEYCODE_DPAD_RIGHT; meta = shift; break;
      case DrnasRadialPaletteModel.FRAME_SELECTED: key = android.view.KeyEvent.KEYCODE_NUMPAD_DOT; meta = 0; break;
      case DrnasRadialPaletteModel.FRAME_ALL: key = android.view.KeyEvent.KEYCODE_MOVE_HOME; meta = 0; break;
      case DrnasRadialPaletteModel.FRONT_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_1; meta = 0; break;
      case DrnasRadialPaletteModel.RIGHT_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_3; meta = 0; break;
      case DrnasRadialPaletteModel.TOP_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_7; meta = 0; break;
      case DrnasRadialPaletteModel.BACK_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_1; meta = ctrl; break;
      case DrnasRadialPaletteModel.LEFT_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_3; meta = ctrl; break;
      case DrnasRadialPaletteModel.BOTTOM_VIEW: key = android.view.KeyEvent.KEYCODE_NUMPAD_7; meta = ctrl; break;
      case DrnasRadialPaletteModel.PERSPECTIVE: key = android.view.KeyEvent.KEYCODE_NUMPAD_5; meta = 0; break;
      case DrnasRadialPaletteModel.SEARCH: key = android.view.KeyEvent.KEYCODE_F3; meta = 0; break;
      case DrnasRadialPaletteModel.HIDE: key = android.view.KeyEvent.KEYCODE_H; meta = 0; break;
      default: return;
    }
    drnasHaptic(source);
    drnasTap(key, meta);
  }

  /* ------------------------------------------------------------------ keyboard extras */

  /** Extra keys appended to the shortcut strip: editing, arrows and keyboard control. */
  private void installDrnasKeyExtras(LinearLayout row) {
    drnasSmallKey("Redo", android.view.KeyEvent.KEYCODE_Z,
        android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_SHIFT_ON, row);
    final Button backspace = drnasSmallKey("\u232B", android.view.KeyEvent.KEYCODE_DEL, 0, row);
    backspace.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
        "Backspace", "Brisanje ulijevo"));
    drnasSmallKey("\u2190", android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0, row);
    drnasSmallKey("\u2192", android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0, row);
    drnasSmallKey("\u2191", android.view.KeyEvent.KEYCODE_DPAD_UP, 0, row);
    drnasSmallKey("\u2193", android.view.KeyEvent.KEYCODE_DPAD_DOWN, 0, row);
    drnasSmallKey("Save As", android.view.KeyEvent.KEYCODE_S,
        android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_SHIFT_ON, row);
    final Button hide = drnasSmallKey("Kb \u2715", 0, 0, row);
    hide.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
        "Hide the Android keyboard", "Sakrij Android tipkovnicu"));
    hide.setOnClickListener(v -> hideKeyboard());
    final Button clear = drnasSmallKey("Clr", 0, 0, row);
    clear.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
        "Release all latched keys and mouse buttons", "Otpusti sve zakljucane tipke i tipke misa"));
    clear.setOnClickListener(v -> drnasClearModifiers());
  }

  /** Drops every latched modifier and latched mouse button. */
  private void drnasClearModifiers() {
    final int[] flags = {KeyEvent.META_CTRL_ON, KeyEvent.META_SHIFT_ON, KeyEvent.META_ALT_ON};
    final int[] keys = {KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT,
                        KeyEvent.KEYCODE_ALT_LEFT};
    for (int i = 0; i < flags.length; i++) {
      if ((drnasStickyModifiers & flags[i]) != 0) {
        drnasStickyModifiers &= ~flags[i];
        nativeOnKey(keys[i], KeyEvent.ACTION_UP, drnasStickyModifiers);
      }
    }
    drnasReleaseAllInputs();
    drnasReleaseTouchInput("clear");
  }

  /* ------------------------------------------------------------------ layout */

  private int drnasSafeInset(boolean top) {
    try {
      final android.view.WindowInsets insets =
          getWindow().getDecorView().getRootWindowInsets();
      if (insets == null) {
        return 0;
      }
      if (android.os.Build.VERSION.SDK_INT >= 30) {
        final android.graphics.Insets bars = insets.getInsets(
            android.view.WindowInsets.Type.systemBars()
                | android.view.WindowInsets.Type.displayCutout());
        return top ? bars.top : bars.bottom;
      }
      return top ? insets.getSystemWindowInsetTop() : insets.getSystemWindowInsetBottom();
    } catch (Throwable ignored) {
      return 0;
    }
  }

  /**
   * Pulls every floating group back inside the safe area. Called after rotation, a window
   * resize, a keyboard inset change and after any preset, so a saved layout can never become
   * unreachable.
   */
  private DrnasControlLayout.Bounds drnasLayoutBounds() {
    final View decor = getWindow().getDecorView();
    final DrnasControlLayout.Bounds bounds = new DrnasControlLayout.Bounds(
        decor == null ? 1 : Math.max(1, decor.getWidth()),
        decor == null ? 1 : Math.max(1, decor.getHeight()),
        Math.max(1, Math.round(getResources().getDisplayMetrics().density)));
    bounds.insetTop = drnasSafeInset(true);
    bounds.insetBottom = drnasSafeInset(false);
    bounds.insetLeft = drnasSafeInsetLeft();
    bounds.insetRight = drnasSafeInsetRight();
    return bounds;
  }

  /** Horizontal safe insets (display cutouts on a landscape phone, gesture areas in portrait). */
  private int drnasSafeInsetLeft() {
    try {
      if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow() != null) {
        final android.view.WindowInsets insets =
            getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
          return insets.getInsets(
              android.view.WindowInsets.Type.displayCutout()
              | android.view.WindowInsets.Type.systemBars()).left;
        }
      }
    } catch (Throwable ignored) {
      /* Safe areas are a nicety: never break layout over them. */
    }
    return 0;
  }

  private int drnasSafeInsetRight() {
    try {
      if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow() != null) {
        final android.view.WindowInsets insets =
            getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
          return insets.getInsets(
              android.view.WindowInsets.Type.displayCutout()
              | android.view.WindowInsets.Type.systemBars()).right;
        }
      }
    } catch (Throwable ignored) {
      /* As above. */
    }
    return 0;
  }

  private void drnasClampLayout() {
    final View decor = getWindow().getDecorView();
    if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
      return;
    }
    final DrnasControlLayout.Bounds bounds = drnasLayoutBounds();
    drnasClampPanel(drnasMouseDock, "zoom", bounds);
    drnasClampPanel(drnasKeyStrip, "shortcut_strip", bounds);
    drnasClampPanel(drnasNavBar, "nav", bounds);
    drnasClampPanel(drnasToolPaletteView, "tools", bounds);
    drnasClampPanel(drnasModeChrome, "mode_badge", bounds);
    drnasRefreshPanels();
  }

  private void drnasClampPanel(View view, String key,
                               DrnasControlLayout.Bounds bounds) {
    if (view == null) {
      return;
    }
    final DrnasPanel panel = drnasPanels.get(view);
    if (panel == null || panel.lastWidth <= 0 || panel.lastHeight <= 0) {
      return;
    }
    final float baseX = panel.lastX - drnasOffsetX(view);
    final float baseY = panel.lastY - drnasOffsetY(view);
    // Insets come from drnasLayoutBounds(), so a panel can never be restored under the status
    // bar, a cutout or the navigation bar.
    final float clampedX = DrnasControlLayout.clampInBounds(
        drnasOffsetX(view), baseX, panel.lastWidth, bounds, true);
    final float clampedY = DrnasControlLayout.clampInBounds(
        drnasOffsetY(view), baseY, panel.lastHeight, bounds, false);
    if (clampedX != drnasOffsetX(view)) {
      drnasSetOffsetX(view, clampedX);
    }
    if (clampedY != drnasOffsetY(view)) {
      drnasSetOffsetY(view, clampedY);
    }
    drnasPrefs.edit()
        .putFloat(key + "_x", drnasOffsetX(view))
        .putFloat(key + "_y", drnasOffsetY(view))
        .apply();
  }

  private void drnasApplyOverlayVisibility() {
    final boolean show = !drnasOverlayHidden;
    if (drnasMouseDock != null) {
      drnasPanelVisibility(drnasMouseDock,
          show && drnasPrefs.getBoolean("mouse_visible", true) ? View.VISIBLE : View.GONE);
    }
    if (drnasKeyStrip != null) {
      drnasPanelVisibility(drnasKeyStrip,
          show && drnasPrefs.getBoolean("keys_visible", true) ? View.VISIBLE : View.GONE);
    }
    if (drnasNavBar != null) {
      drnasPanelVisibility(drnasNavBar,
          show && drnasPrefs.getBoolean("nav_visible", true) ? View.VISIBLE : View.GONE);
    }
    if (drnasToolPaletteView != null) {
      drnasPanelVisibility(drnasToolPaletteView,
          show && drnasPrefs.getBoolean("tools_visible", true) ? View.VISIBLE : View.GONE);
    }
  }

  private void drnasToggleOverlay() {
    drnasOverlayHidden = !drnasOverlayHidden;
    if (drnasOverlayHidden) {
      drnasReleaseTouchInput("overlay-hidden");
    }
    drnasApplyOverlayVisibility();
    drnasRefreshPanels();
  }

  private void drnasApplyLayoutPreset(int preset) {
    final DrnasControlLayout.Group mouse = new DrnasControlLayout.Group();
    final DrnasControlLayout.Group keys = new DrnasControlLayout.Group();
    final DrnasControlLayout.Group nav = new DrnasControlLayout.Group();
    final DrnasControlLayout.Group tools = new DrnasControlLayout.Group();
    mouse.offsetX = drnasPrefs.getFloat("zoom_x", 0f);
    mouse.offsetY = drnasPrefs.getFloat("zoom_y", 0f);
    mouse.buttonPercent = drnasPrefs.getInt("mouse_button_percent", 100);
    mouse.opacityPercent = drnasPrefs.getInt("mouse_transparency", 0);
    mouse.visible = drnasPrefs.getBoolean("mouse_visible", true);
    keys.offsetX = drnasPrefs.getFloat("shortcut_strip_x", 0f);
    keys.offsetY = drnasPrefs.getFloat("shortcut_strip_y", 0f);
    keys.buttonPercent = Math.max(60, Math.min(150, drnasPrefs.getInt("button_percent", 100)));
    keys.opacityPercent = drnasPrefs.getInt("keyboard_transparency", 0);
    keys.rows = drnasPrefs.getInt("keyboard_rows", 2);
    keys.expanded = drnasPrefs.getBoolean("shortcut_expanded", true);
    keys.visible = drnasPrefs.getBoolean("keys_visible", true);
    nav.offsetX = drnasPrefs.getFloat("nav_x", 0f);
    nav.offsetY = drnasPrefs.getFloat("nav_y", 0f);
    nav.visible = drnasPrefs.getBoolean("nav_visible", true);
    nav.expanded = drnasPrefs.getBoolean("nav_expanded", true);
    tools.offsetX = drnasPrefs.getFloat("tools_x", 0f);
    tools.offsetY = drnasPrefs.getFloat("tools_y", 0f);
    tools.buttonPercent = drnasPrefs.getInt("tool_button_percent", 100);
    tools.opacityPercent = drnasPrefs.getInt("tools_transparency", 0);
    tools.visible = drnasPrefs.getBoolean("tools_visible", true);

    DrnasControlLayout.applyPreset(preset, mouse, keys, nav, tools);

    drnasPrefs.edit()
        .putFloat("zoom_x", mouse.offsetX)
        .putFloat("zoom_y", mouse.offsetY)
        .putInt("mouse_button_percent", mouse.buttonPercent)
        .putInt("mouse_transparency", mouse.opacityPercent)
        .putBoolean("mouse_visible", mouse.visible)
        .putFloat("shortcut_strip_x", keys.offsetX)
        .putFloat("shortcut_strip_y", keys.offsetY)
        .putInt("button_percent", keys.buttonPercent)
        .putInt("keyboard_transparency", keys.opacityPercent)
        .putInt("keyboard_rows", keys.rows)
        .putBoolean("shortcut_expanded", keys.expanded)
        .putBoolean("keys_visible", keys.visible)
        .putFloat("nav_x", nav.offsetX)
        .putFloat("nav_y", nav.offsetY)
        .putBoolean("nav_visible", nav.visible)
        .putBoolean("nav_expanded", nav.expanded)
        .putFloat("tools_x", tools.offsetX)
        .putFloat("tools_y", tools.offsetY)
        .putInt("tool_button_percent", tools.buttonPercent)
        .putInt("tools_transparency", tools.opacityPercent)
        .putBoolean("tools_visible", tools.visible)
        .putInt("layout_preset", preset)
        .apply();

    drnasOverlayHidden = false;
    drnasMouseButtonSizes(mouse.buttonPercent, false);
    drnasToolPaletteSize(tools.buttonPercent, false);
    drnasButtonSizes(keys.buttonPercent, false);
    drnasApplyOpacity();
    drnasApplyOverlayVisibility();
    drnasClampLayout();
    drnasRefreshPanels();
  }


  // ---------------------------------------------------------------- shortcut strip arrangement
  private DrnasShortcutStrip drnasStrip;
  private final java.util.LinkedHashMap<String, View> drnasStripViews =
      new java.util.LinkedHashMap<>();
  private final java.util.LinkedHashMap<String, ViewGroup> drnasStripParents =
      new java.util.LinkedHashMap<>();

  /**
   * Registers the already-built shortcut buttons so the user can reorder and hide them. The views
   * are the ones the strip already uses - nothing is rebuilt, so listeners and key bindings stay
   * exactly as the rest of the activity created them.
   */
  private void drnasBindShortcutStrip(LinearLayout strip) {
    drnasStripViews.clear();
    drnasStripParents.clear();
    final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
    for (int rowIndex = 0; rowIndex < strip.getChildCount(); rowIndex++) {
      final View row = strip.getChildAt(rowIndex);
      if (!(row instanceof ViewGroup)) {
        continue;
      }
      final ViewGroup group = (ViewGroup) row;
      for (int i = 0; i < group.getChildCount(); i++) {
        final View child = group.getChildAt(i);
        if (!(child instanceof Button)) {
          continue;
        }
        final String label = ((Button) child).getText().toString();
        if (label.length() == 0 || drnasStripViews.containsKey(label)) {
          continue;
        }
        drnasStripViews.put(label, child);
        drnasStripParents.put(label, group);
        labels.add(label);
      }
    }
    drnasStrip = new DrnasShortcutStrip(labels.toArray(new String[0]));
    drnasStrip.applyPref(drnasPrefs.getString(DrnasShortcutStrip.PREF_KEY, ""));
    drnasApplyShortcutStrip();
  }

  private void drnasSaveShortcutStrip() {
    if (drnasStrip == null) {
      return;
    }
    drnasPrefs.edit().putString(DrnasShortcutStrip.PREF_KEY, drnasStrip.encode()).apply();
  }

  /** Applies the saved order and visibility to the real strip views. */
  private void drnasApplyShortcutStrip() {
    if (drnasStrip == null || drnasStripViews.isEmpty()) {
      return;
    }
    final java.util.LinkedHashSet<ViewGroup> parents =
        new java.util.LinkedHashSet<>(drnasStripParents.values());
    for (ViewGroup parent : parents) {
      for (int slot = 0; slot < drnasStrip.size(); slot++) {
        final String label = drnasStrip.labelAt(slot);
        final View view = label == null ? null : drnasStripViews.get(label);
        if (view == null || drnasStripParents.get(label) != parent) {
          continue;
        }
        parent.removeView(view);
        view.setVisibility(drnasStrip.isHidden(slot) ? View.GONE : View.VISIBLE);
        parent.addView(view);
      }
    }
  }

  /** Editable list of the strip: move, hide/restore and reset, persisted on every change. */
  private void drnasBuildStripArranger(final LinearLayout container) {
    container.removeAllViews();
    if (drnasStrip == null) {
      final TextView empty = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
          "Shortcut strip not available", "Traka prečaca nije dostupna"));
      empty.setTextSize(10f);
      container.addView(empty);
      return;
    }
    for (int slot = 0; slot < drnasStrip.size(); slot++) {
      final int index = slot;
      final String label = drnasStrip.labelAt(slot);
      final LinearLayout line = new LinearLayout(this);
      line.setGravity(Gravity.CENTER_VERTICAL);
      final Button left = drnasSmallKey("\u25C0", 0, 0, line);
      left.setEnabled(slot > 0);
      left.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
          "Move earlier: ", "Pomakni ranije: ") + label);
      left.setOnClickListener(v -> {
        if (drnasStrip.move(index, -1)) {
          drnasSaveShortcutStrip();
          drnasApplyShortcutStrip();
          drnasBuildStripArranger(container);
        }
      });
      final Button right = drnasSmallKey("\u25B6", 0, 0, line);
      right.setEnabled(slot < drnasStrip.size() - 1);
      right.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
          "Move later: ", "Pomakni kasnije: ") + label);
      right.setOnClickListener(v -> {
        if (drnasStrip.move(index, 1)) {
          drnasSaveShortcutStrip();
          drnasApplyShortcutStrip();
          drnasBuildStripArranger(container);
        }
      });
      final Button toggle = drnasSmallKey(drnasStrip.isHidden(slot) ? "+" : "\u2715", 0, 0, line);
      toggle.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
          "Show or hide: ", "Prikaži ili sakrij: ") + label);
      toggle.setOnClickListener(v -> {
        drnasStrip.toggleHidden(index);
        drnasSaveShortcutStrip();
        drnasApplyShortcutStrip();
        drnasBuildStripArranger(container);
      });
      final TextView name = drnasUiLabel(label);
      name.setTextSize(11f);
      if (drnasStrip.isHidden(slot)) {
        name.setAlpha(0.45f);
      }
      name.setPadding(dpDrnas(6), 0, 0, 0);
      line.addView(name, new LinearLayout.LayoutParams(0, dpDrnas(34), 1f));
      container.addView(line);
    }
    final Button reset = drnasSmallKey(DrnasLanguage.text(BlenderActivity.this,
        "Reset shortcut strip", "Vrati zadanu traku prečaca"), 0, 0, container);
    reset.setOnClickListener(v -> {
      drnasStrip.reset();
      drnasSaveShortcutStrip();
      drnasApplyShortcutStrip();
      drnasBuildStripArranger(container);
    });
  }


  /**
   * Gesture switches. Every gesture can be turned off independently: a user who keeps orbiting by
   * accident while scrolling, or who does not want pinching to zoom, can disable exactly that one
   * without losing the others.
   */
  private void installDrnasGestureOptions(LinearLayout options) {
    options.addView(drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "GESTURES", "GESTE")));
    final String[] keys = {"gesture_orbit", "gesture_pinch", "gesture_pan", "gesture_rmb"};
    final String[] names = {
        DrnasLanguage.text(BlenderActivity.this, "Two fingers: orbit", "Dva prsta: rotacija"),
        DrnasLanguage.text(BlenderActivity.this, "Pinch: zoom", "Štipanje: zum"),
        DrnasLanguage.text(BlenderActivity.this, "Three fingers: pan", "Tri prsta: pomicanje"),
        DrnasLanguage.text(BlenderActivity.this, "Two-finger tap: right click",
            "Dodir s dva prsta: desni klik")};
    for (int i = 0; i < keys.length; i++) {
      final String key = keys[i];
      final android.widget.Switch toggle = new android.widget.Switch(this);
      toggle.setText(names[i]);
      toggle.setTextColor(drnasTextWhite);
      toggle.setTextSize(12f);
      drnasStyleSwitch(toggle);
      toggle.setChecked(drnasPrefs.getBoolean(key, true));
      toggle.setOnCheckedChangeListener((v, enabled) -> {
        drnasPrefs.edit().putBoolean(key, enabled).apply();
        drnasApplyGesturePrefs();
      });
      options.addView(toggle);
    }
    final TextView sensitivityLabel = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Gesture sensitivity: ", "Osjetljivost gesti: ")
        + drnasPrefs.getInt("gesture_sensitivity", 100) + "%");
    options.addView(sensitivityLabel);
    final SeekBar sensitivity = new SeekBar(this);
    drnasStyleSeekBar(sensitivity);
    sensitivity.setMax(150);
    sensitivity.setProgress(Math.max(0, Math.min(150, drnasPrefs.getInt("gesture_sensitivity", 100) - 50)));
    sensitivity.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
        "Lower values require a longer finger movement before a gesture starts",
        "Niže vrijednosti traže dulje pomicanje prsta prije geste"));
    sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
        final int percent = value + 50;
        sensitivityLabel.setText(DrnasLanguage.text(BlenderActivity.this,
            "Gesture sensitivity: ", "Osjetljivost gesti: ") + percent + "%");
        if (fromUser) {
          drnasPrefs.edit().putInt("gesture_sensitivity", percent).apply();
          drnasApplyGesturePrefs();
        }
      }
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) {}
    });
    options.addView(sensitivity, new LinearLayout.LayoutParams(-1, dpDrnas(38)));

    final TextView accelerationLabel = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Pointer acceleration: ", "Ubrzanje pokazivača: ")
        + drnasPrefs.getInt("pointer_acceleration", 35) + "%");
    options.addView(accelerationLabel);
    final SeekBar acceleration = new SeekBar(this);
    drnasStyleSeekBar(acceleration);
    acceleration.setMax(150);
    acceleration.setProgress(Math.max(0, Math.min(150,
        drnasPrefs.getInt("pointer_acceleration", 35))));
    acceleration.setContentDescription(DrnasLanguage.text(BlenderActivity.this,
        "Extra pointer speed for fast swipes; zero keeps the motion linear",
        "Dodatna brzina za brze pokrete; nula zadržava linearno kretanje"));
    acceleration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
        accelerationLabel.setText(DrnasLanguage.text(BlenderActivity.this,
            "Pointer acceleration: ", "Ubrzanje pokazivača: ") + value + "%");
        if (fromUser) {
          drnasPrefs.edit().putInt("pointer_acceleration", value).apply();
          drnasPadMotion.setAcceleration(value / 100f);
        }
      }
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) {}
    });
    options.addView(acceleration, new LinearLayout.LayoutParams(-1, dpDrnas(38)));
  }

  private void installDrnasLayoutOptions(LinearLayout options) {
    options.addView(drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "CONTROL LAYOUT PRESETS", "PRESETI RASPOREDA KONTROLA")));
    final LinearLayout row1 = new LinearLayout(this);
    final LinearLayout row2 = new LinearLayout(this);
    row1.setGravity(Gravity.CENTER_VERTICAL);
    row2.setGravity(Gravity.CENTER_VERTICAL);
    final int[] presets = {
        DrnasControlLayout.PRESET_TWO_THUMB,
        DrnasControlLayout.PRESET_ONE_HAND,
        DrnasControlLayout.PRESET_COMPACT,
        DrnasControlLayout.PRESET_LARGE_TARGETS,
        DrnasControlLayout.PRESET_TABLET,
        DrnasControlLayout.PRESET_DESKTOP};
    final String[] shortNames = {"2 thumbs", "One hand", "Compact", "Large", "Tablet", "Desktop"};
    for (int i = 0; i < presets.length; i++) {
      final int preset = presets[i];
      final Button button = new Button(this);
      button.setAllCaps(false);
      button.setText(shortNames[i]);
      button.setTextSize(11f);
      button.setContentDescription(DrnasControlLayout.presetName(preset));
      drnasStyleKey(button, false);
      ((i < 3) ? row1 : row2).addView(button,
          new LinearLayout.LayoutParams(0, dpDrnas(42), 1f));
      button.setOnClickListener(v -> drnasApplyLayoutPreset(preset));
    }
    options.addView(row1);
    options.addView(row2);

    options.addView(drnasUiLabel("RADIAL TOOL PAGES  •  Object / Edit / Timeline / View"));
    final android.widget.Switch toolsVisible = new android.widget.Switch(this);
    toolsVisible.setText("Show movable radial tool palette");
    toolsVisible.setTextColor(DrnasSkin.TEXT);
    drnasStyleSwitch(toolsVisible);
    toolsVisible.setChecked(drnasPrefs.getBoolean("tools_visible", true));
    toolsVisible.setOnCheckedChangeListener((v, visible) -> {
      drnasPrefs.edit().putBoolean("tools_visible", visible).apply();
      if (drnasToolPaletteView != null) {
        drnasPanelVisibility(drnasToolPaletteView, visible ? View.VISIBLE : View.GONE);
      }
    });
    options.addView(toolsVisible);
    drnasEditorSlider(options, "Radial tool size", 90, 150,
        drnasPrefs.getInt("tool_button_percent", 100), "%",
        value -> drnasToolPaletteSize(value, true));
    drnasEditorSlider(options, "Tool palette opacity", 0, 85,
        drnasPrefs.getInt("tools_transparency", 0), "%", value -> {
          drnasPrefs.edit().putInt("tools_transparency", value).apply();
          drnasApplyOpacity();
        });

    final LinearLayout row3 = new LinearLayout(this);
    row3.setGravity(Gravity.CENTER_VERTICAL);
    final Button reset = new Button(this);
    reset.setAllCaps(false);
    reset.setText(DrnasLanguage.text(BlenderActivity.this, "Reset layout", "Vrati raspored"));
    reset.setTextSize(11f);
    drnasStyleKey(reset, false);
    row3.addView(reset, new LinearLayout.LayoutParams(0, dpDrnas(42), 1f));
    reset.setOnClickListener(v -> {
      final android.content.SharedPreferences.Editor editor = drnasPrefs.edit();
      for (String key : new String[] {"zoom_x", "zoom_y", "shortcut_strip_x",
          "shortcut_strip_y", "nav_x", "nav_y", "mode_badge_x", "mode_badge_y",
          "mouse_transparency", "keyboard_transparency", "mouse_width", "mouse_height",
          "keyboard_width", "keyboard_height", "keyboard_order", "layout_preset",
          "pointer_acceleration", "nav_speed", "mouse_visible", "keys_visible",
          "nav_visible", "tools_x", "tools_y", "tool_button_percent", "tools_transparency",
          "tools_visible", "tool_page"}) {
        editor.remove(key);
      }
      editor.putInt("mouse_button_percent", 100)
          .putInt("button_percent", 100)
          .putBoolean("shortcut_expanded", true)
          .putBoolean("nav_expanded", true)
          .apply();
      drnasApplyLayoutPreset(DrnasControlLayout.PRESET_TWO_THUMB);
    });
    final Button hide = new Button(this);
    hide.setAllCaps(false);
    hide.setText(DrnasLanguage.text(BlenderActivity.this, "Hide / show", "Sakrij / prikazi"));
    hide.setTextSize(11f);
    drnasStyleKey(hide, false);
    row3.addView(hide, new LinearLayout.LayoutParams(0, dpDrnas(42), 1f));
    hide.setOnClickListener(v -> drnasToggleOverlay());
    options.addView(row3);

    options.addView(drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Tap the mode badge to switch TOUCH / PAD",
        "Dodirni oznaku nacina za TOUCH / PAD")));
    final TextView acceleration = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Pointer acceleration: ", "Ubrzanje pokazivaca: ")
        + drnasPrefs.getInt("pointer_acceleration", 35) + "%");
    options.addView(acceleration);
    final SeekBar accelerationBar = new SeekBar(this);
    drnasStyleSeekBar(accelerationBar);
    accelerationBar.setMax(100);
    accelerationBar.setProgress(drnasPrefs.getInt("pointer_acceleration", 35));
    options.addView(accelerationBar, new LinearLayout.LayoutParams(-1, dpDrnas(38)));
    accelerationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) {}
      public void onProgressChanged(SeekBar bar, int progress, boolean user) {
        acceleration.setText(DrnasLanguage.text(BlenderActivity.this,
            "Pointer acceleration: ", "Ubrzanje pokazivaca: ") + progress + "%");
        if (user) {
          drnasPrefs.edit().putInt("pointer_acceleration", progress).apply();
          drnasSyncPointerGeometry();
        }
      }
    });

    final TextView navSpeedLabel = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Navigation pad speed: ", "Brzina navigacije: ") + Math.round(drnasNavSpeed * 100) + "%");
    options.addView(navSpeedLabel);
    final SeekBar navSpeedBar = new SeekBar(this);
    drnasStyleSeekBar(navSpeedBar);
    navSpeedBar.setMax(360);
    navSpeedBar.setProgress(Math.max(40, Math.min(400, (int) Math.round(drnasNavSpeed * 100))));
    options.addView(navSpeedBar, new LinearLayout.LayoutParams(-1, dpDrnas(38)));
    navSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) {}
      public void onProgressChanged(SeekBar bar, int progress, boolean user) {
        if (progress < 40) {
          return;
        }
        drnasNavSpeed = progress / 100f;
        navSpeedLabel.setText(DrnasLanguage.text(BlenderActivity.this,
            "Navigation pad speed: ", "Brzina navigacije: ") + progress + "%");
        if (user) {
          drnasPrefs.edit().putInt("nav_speed", progress).apply();
        }
      }
    });

    final android.widget.Switch haptics = new android.widget.Switch(this);
    haptics.setText(DrnasLanguage.text(BlenderActivity.this,
        "Haptic feedback", "Vibracija"));
    haptics.setTextColor(drnasTextWhite);
    haptics.setTextSize(12f);
    drnasStyleSwitch(haptics);
    haptics.setChecked(drnasPrefs.getBoolean("haptics", true));
    haptics.setOnCheckedChangeListener((v, enabled) -> {
      drnasPrefs.edit().putBoolean("haptics", enabled).apply();
      if (enabled) {
        drnasHaptic(v);
      }
    });
    options.addView(haptics);

    final TextView hapticsNote = drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "Latched buttons show a dot; tap Clr to release everything",
        "Zakljucane tipke imaju tocku; Clr otpusta sve"));
    hapticsNote.setTextSize(10f);
    options.addView(hapticsNote);

    options.addView(drnasUiLabel(DrnasLanguage.text(BlenderActivity.this,
        "SHORTCUT STRIP ORDER", "REDOSLIJED TRAKE PREČACA")));
    final LinearLayout stripArranger = new LinearLayout(this);
    stripArranger.setOrientation(LinearLayout.VERTICAL);
    options.addView(stripArranger);
    drnasBuildStripArranger(stripArranger);

    installDrnasGestureOptions(options);
  }
