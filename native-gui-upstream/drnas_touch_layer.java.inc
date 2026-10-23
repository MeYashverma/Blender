  /* ==================================================================
   * DRNAS touch control layer.
   *
   * Everything below is glue only: the decisions live in the unit tested
   * DrnasPointerMap / DrnasGestureEngine / DrnasRelativePad / DrnasMouseState
   * classes. This keeps the Android-specific part small and reviewable.
   * ================================================================== */

  private static final int DRNAS_CAP_MIDDLE = 1;
  private static final int DRNAS_CAP_SHIFT = 2;
  private static final int DRNAS_CAP_HSCROLL = 4;
  private static final int DRNAS_CAP_KEYS = 8;
  private static final int DRNAS_CAP_GESTURES = 16;

  private static final int DRNAS_MODE_DIRECT = 0;
  private static final int DRNAS_MODE_RELATIVE = 1;

  /* view space -> native space, decided once, never applied twice. */
  private final DrnasPointerMap drnasMap = new DrnasPointerMap();
  private final DrnasRelativePad drnasPadMotion = new DrnasRelativePad();
  private DrnasGestureEngine drnasGestures;
  private DrnasMouseState drnasMouseButtons;
  private DrnasMouseButtonModel drnasMouseButtonModel;

  private int drnasInputMode = DRNAS_MODE_DIRECT;
  private int drnasCaps = -1;
  private boolean drnasTouchReady;
  private View drnasModeChrome;
  private TextView drnasModeBadge;
  private boolean drnasOverlayHidden;

  /**
   * 0 for an older libblender.so that has no probe: the overlay then keeps to the stock input
   * bridge (LMB/RMB/wheel/ASCII keys) and disables what genuinely cannot work.
   */
  private int drnasNativeCapabilities() {
    if (drnasCaps >= 0) {
      return drnasCaps;
    }
    try {
      drnasCaps = nativeDrnasCapabilities();
    } catch (Throwable legacyLibrary) {
      drnasCaps = 0;
    }
    return drnasCaps;
  }

  private boolean drnasHasCapability(int capability) {
    return (drnasNativeCapabilities() & capability) != 0;
  }

  /* ------------------------------------------------------------------ setup */

  private void drnasSetupTouch() {
    drnasInputMode = drnasPrefs.getInt("input_mode",
        drnasPrefs.getBoolean("relative_mouse", false) ? DRNAS_MODE_RELATIVE : DRNAS_MODE_DIRECT);
    drnasRelativeMouse = drnasInputMode == DRNAS_MODE_RELATIVE;
    drnasMouseSpeed = Math.max(0.25f, Math.min(3f, drnasPrefs.getInt("mouse_speed", 100) / 100f));
    drnasPointerEffect = drnasPrefs.getBoolean("pointer_effect", true);
    drnasCursorPercent = Math.max(40, Math.min(150, drnasPrefs.getInt("cursor_percent", 70)));
    drnasNavSpeed = Math.max(0.4f, Math.min(4f, drnasPrefs.getInt("nav_speed", 160) / 100f));
    drnasMouseButtons = new DrnasMouseState((bit, down) -> drnasEmitMouse(bit, down));
    drnasMouseButtons.setMiddleAvailable(drnasHasCapability(DRNAS_CAP_MIDDLE));
    drnasMouseButtonModel = new DrnasMouseButtonModel((bit, down) -> {
      if (drnasMouseButtons == null) return;
      if (down) drnasMouseButtons.press(bit);
      else drnasMouseButtons.release(bit);
      for (DrnasMouseButtonView button : drnasMouseButtonViews) button.invalidate();
    }, android.view.ViewConfiguration.getDoubleTapTimeout());
    drnasMouseButtonModel.setMiddleAvailable(drnasHasCapability(DRNAS_CAP_MIDDLE));
    final boolean canOrbit = drnasHasCapability(DRNAS_CAP_MIDDLE);
    final boolean canPan = canOrbit && drnasHasCapability(DRNAS_CAP_SHIFT);
    drnasGestures = new DrnasGestureEngine(drnasGestureSink);
    drnasGestures.setDragSlop(drnasGestureSlop());
    drnasGestures.setDecideSlop(drnasGestureSlop() * 1.5f);
    drnasGestures.setTwoFingerOrbit(canOrbit && drnasPrefs.getBoolean("gesture_orbit", true));
    drnasGestures.setThreeFingerPan(canPan && drnasPrefs.getBoolean("gesture_pan", true));
    drnasGestures.setPinchZoom(drnasPrefs.getBoolean("gesture_pinch", true));
    drnasGestures.setTwoFingerTapRightClick(drnasPrefs.getBoolean("gesture_rmb", true));
    drnasGestures.setZoomNotchesPerPixel(
        Math.max(0.005f, Math.min(0.08f, drnasPrefs.getInt("zoom_rate", 2) / 100f)));
    drnasTouchReady = true;
    drnasSyncPointerGeometry();
    if (drnasInputMode == DRNAS_MODE_RELATIVE) {
      drnasPadMotion.centre();
      drnasPointerX = drnasPadMotion.x();
      drnasPointerY = drnasPadMotion.y();
    }
  }

  /**
   * Finger travel before a gesture is recognised. The sensitivity preference scales it, so a user
   * who keeps triggering drags by accident can raise the threshold without touching code.
   */
  private float drnasGestureSlop() {
    final int percent = Math.max(50, Math.min(200, drnasPrefs.getInt("gesture_sensitivity", 100)));
    return android.view.ViewConfiguration.get(this).getScaledTouchSlop() * 100f / percent;
  }

  /** Re-applies every gesture preference and capability gate without rebuilding the overlay. */
  private void drnasApplyGesturePrefs() {
    if (drnasGestures == null) {
      return;
    }
    final boolean canOrbit = drnasHasCapability(DRNAS_CAP_MIDDLE);
    final boolean canPan = canOrbit && drnasHasCapability(DRNAS_CAP_SHIFT);
    drnasGestures.setDragSlop(drnasGestureSlop());
    drnasGestures.setDecideSlop(drnasGestureSlop() * 1.5f);
    drnasGestures.setTwoFingerOrbit(canOrbit && drnasPrefs.getBoolean("gesture_orbit", true));
    drnasGestures.setThreeFingerPan(canPan && drnasPrefs.getBoolean("gesture_pan", true));
    drnasGestures.setPinchZoom(drnasPrefs.getBoolean("gesture_pinch", true));
    drnasGestures.setTwoFingerTapRightClick(drnasPrefs.getBoolean("gesture_rmb", true));
    drnasGestures.setZoomNotchesPerPixel(
        Math.max(0.005f, Math.min(0.08f, drnasPrefs.getInt("zoom_rate", 2) / 100f)));
  }

  /** Refreshes the single coordinate model from the current window geometry. */
  private void drnasSyncPointerGeometry() {
    View decor = getWindow().getDecorView();
    if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
      return;
    }
    final int nativeWidth = decor.getWidth();
    final int nativeHeight = decor.getHeight();
    final View pad = drnasMousePad;
    final int viewWidth = pad != null && pad.getWidth() > 0 ? pad.getWidth() : nativeWidth;
    final int viewHeight = pad != null && pad.getHeight() > 0 ? pad.getHeight() : nativeHeight;
    drnasMap.setNativeSize(nativeWidth, nativeHeight);
    drnasMap.setViewSize(viewWidth, viewHeight);
    // A pad that covers the whole surface is mapped by fitting. A pad that sits inside a smaller
    // floating host (dialog, shrunken window) must be mapped 1:1 from its own origin, otherwise a
    // stretched display would apply the scale twice.
    if (pad != null && pad.getWidth() > 0
        && (pad.getWidth() < nativeWidth - 1 || pad.getHeight() < nativeHeight - 1)) {
      final int[] padLocation = new int[2];
      final int[] decorLocation = new int[2];
      pad.getLocationOnScreen(padLocation);
      decor.getLocationOnScreen(decorLocation);
      drnasMap.setMode(DrnasPointerMap.MODE_SUBRECT);
      drnasMap.setViewOrigin(padLocation[0] - decorLocation[0], padLocation[1] - decorLocation[1]);
    } else {
      drnasMap.setMode(DrnasPointerMap.MODE_FIT);
    }
    drnasPadMotion.setBounds(drnasMap.maxNativeX(), drnasMap.maxNativeY());
    drnasPadMotion.setSpeed(Math.max(0.25f, Math.min(3f, drnasMouseSpeed)));
    drnasPadMotion.setAcceleration(drnasPrefs.getInt("pointer_acceleration", 35) / 100f);
  }

  /* ------------------------------------------------------------------ native emission */

  /**
   * Single low-level emitter. Action numbers match the DRNAS native bridge:
   * 1 cursor, 2/3 LMB, 4/5 RMB, 6 wheel, 7 gesture sensitivity, 8/9 MMB,
   * 10/11 Shift hold, 12 horizontal wheel.
   */
  private void drnasSendInput(int action, float x, float y, float value) {
    switch (action) {
      case 2: drnasHeldMouseButtons |= 1; break;
      case 3: drnasHeldMouseButtons &= ~1; break;
      case 4: drnasHeldMouseButtons |= 2; break;
      case 5: drnasHeldMouseButtons &= ~2; break;
      case 8: drnasHeldMouseButtons |= 4; break;
      case 9: drnasHeldMouseButtons &= ~4; break;
      default: break;
    }
    if (action == 2 || action == 4 || action == 8) {
      drnasClickEffectUntil = android.os.SystemClock.uptimeMillis() + 180;
      if (drnasPointer != null) drnasPointer.postInvalidateDelayed(190);
      drnasHaptic(drnasMousePad != null ? drnasMousePad : getWindow().getDecorView());
    }
    if (action == 6 && drnasSmoothZoomEnabled && drnasSmoothZoomReady) {
      drnasOfferZoom(value);
    } else {
      nativeOnDrnasInput(action, x, y, value);
    }
    if (action >= 2 && action <= 12) {
      drnasUpdatePointer();
    }
  }

  /** Light haptic on a real button press; can be switched off in the layout options. */
  private void drnasHaptic(View view) {
    if (drnasPrefs == null || !drnasPrefs.getBoolean("haptics", true) || view == null) {
      return;
    }
    try {
      view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    } catch (Throwable ignored) {
      /* A device without a vibrator must not break input. */
    }
  }

  private void drnasEmitMouse(int bit, boolean down) {
    final int action;
    if (bit == DrnasMouseState.LEFT) action = down ? 2 : 3;
    else if (bit == DrnasMouseState.RIGHT) action = down ? 4 : 5;
    else if (bit == DrnasMouseState.MIDDLE) action = down ? 8 : 9;
    else return;
    drnasSendInput(action, 0f, 0f, down ? 1f : 0f);
  }

  private final DrnasGestureEngine.Sink drnasGestureSink = new DrnasGestureEngine.Sink() {
    public void pointer(float x, float y, float dx, float dy) {
      drnasOnGesturePointer(x, y, dx, dy);
    }
    public void button(int button, boolean down) {
      if (drnasMouseButtons == null) {
        return;
      }
      final int bit = button == DrnasGestureEngine.BUTTON_LEFT ? DrnasMouseState.LEFT
          : button == DrnasGestureEngine.BUTTON_RIGHT ? DrnasMouseState.RIGHT
          : DrnasMouseState.MIDDLE;
      if (down) {
        drnasMouseButtons.press(bit);
      } else {
        drnasMouseButtons.release(bit);
      }
    }
    public void wheel(int axis, float amount) {
      if (axis == DrnasGestureEngine.WHEEL_HORIZONTAL && !drnasHasCapability(DRNAS_CAP_HSCROLL)) {
        return;
      }
      drnasSendInput(axis == DrnasGestureEngine.WHEEL_HORIZONTAL ? 12 : 6, 0f, 0f, amount);
    }
    public void modifier(int modifier, boolean down) {
      if (modifier != DrnasGestureEngine.MODIFIER_SHIFT
          || !drnasHasCapability(DRNAS_CAP_SHIFT)) {
        return;
      }
      drnasSendInput(down ? 10 : 11, 0f, 0f, down ? 1f : 0f);
    }
    public void gesture(int kind, boolean active) {
      drnasShowGestureState(kind, active);
    }
  };

  /**
   * Relative mode follows the finger deltas through the touchpad model; direct mode maps the
   * absolute finger position. Both end up in native coordinates, which is the only form the
   * cursor sprite and the JNI call use.
   */
  private void drnasOnGesturePointer(float x, float y, float dx, float dy) {
    drnasSyncPointerGeometry();
    if (drnasInputMode == DRNAS_MODE_RELATIVE) {
      drnasPadMotion.move(dx, dy);
      drnasPointerX = drnasPadMotion.x();
      drnasPointerY = drnasPadMotion.y();
    } else {
      drnasPointerX = drnasMap.nativeX(x);
      drnasPointerY = drnasMap.nativeY(y);
    }
    drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
  }

  /** Releases every touch-driven button and modifier. Safe to call repeatedly. */
  private void drnasReleaseTouchInput(String reason) {
    if (drnasMouseSurfaceView != null) drnasMouseSurfaceView.releaseAll();
    if (drnasMouseButtonModel != null) drnasMouseButtonModel.releaseAll();
    if (drnasGestures != null) {
      drnasGestures.releaseAll(reason);
    }
    if (drnasMouseButtons != null) {
      drnasMouseButtons.releaseAll();
    }
    /* Idempotent: the native bridge ignores a Shift release it did not see pressed. */
    drnasSendInput(11, 0f, 0f, 0f);
    drnasShowGestureState(-1, false);
  }

  /* ------------------------------------------------------------------ pointer */

  private void drnasMovePointer(float x, float y) {
    drnasSyncPointerGeometry();
    drnasPointerX = drnasMap.nativeX(x);
    drnasPointerY = drnasMap.nativeY(y);
    if (drnasInputMode == DRNAS_MODE_RELATIVE) {
      drnasPadMotion.setPosition(drnasPointerX, drnasPointerY);
    }
    drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
    drnasUpdatePointer();
  }

  /** Trackpad deltas use the same bounded, accelerated native pointer as relative mode. */
  private void drnasMovePointerRelative(float dx, float dy) {
    drnasSyncPointerGeometry();
    if (drnasPointerX < 0f || drnasPointerY < 0f) {
      drnasPadMotion.centre();
      drnasPointerX = drnasPadMotion.x();
      drnasPointerY = drnasPadMotion.y();
    }
    drnasPadMotion.setPosition(drnasPointerX, drnasPointerY);
    drnasPadMotion.move(dx, dy);
    drnasPointerX = drnasPadMotion.x();
    drnasPointerY = drnasPadMotion.y();
    drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
    drnasUpdatePointer();
  }

  private void drnasUpdatePointer() {
    if (drnasPointerPending) return;
    drnasPointerPending = true;
    getWindow().getDecorView().postOnAnimation(() -> {
      drnasPointerPending = false;
      if (drnasPointerWindow == null) return;
      if (!drnasWindowsActive || drnasInputMode != DRNAS_MODE_RELATIVE) {
        drnasPointerWindow.dismiss();
        return;
      }
      drnasSyncPointerGeometry();
      final float sprite = dpDrnas(38 * drnasCursorPercent / 100f);
      final int[] decorOnScreen = new int[2];
      getWindow().getDecorView().getLocationOnScreen(decorOnScreen);
      final int x = drnasMap.cursorLeft(drnasPointerX, sprite, decorOnScreen[0]);
      final int y = drnasMap.cursorTop(drnasPointerY, sprite, decorOnScreen[1]);
      if (!drnasPointerWindow.isShowing()) {
        drnasPointerWindow.setWidth(Math.round(sprite));
        drnasPointerWindow.setHeight(Math.round(sprite));
        drnasPointerWindow.showAtLocation(getWindow().getDecorView(),
            Gravity.TOP | Gravity.LEFT, x, y);
      } else {
        drnasPointerWindow.update(x, y, Math.round(sprite), Math.round(sprite));
      }
      drnasPointer.invalidate();
    });
  }

  /* ------------------------------------------------------------------ input mode */

  private void drnasSetInputMode(int mode, boolean persist) {
    final int next = mode == DRNAS_MODE_RELATIVE ? DRNAS_MODE_RELATIVE : DRNAS_MODE_DIRECT;
    if (!drnasTouchReady) {
      return;
    }
    drnasReleaseTouchInput("mode-switch");
    drnasInputMode = next;
    drnasRelativeMouse = next == DRNAS_MODE_RELATIVE;
    if (persist && drnasPrefs != null) {
      drnasPrefs.edit()
          .putInt("input_mode", next)
          .putBoolean("relative_mouse", drnasRelativeMouse)
          .apply();
    }
    if (next == DRNAS_MODE_RELATIVE) {
      drnasSyncPointerGeometry();
      drnasPadMotion.centre();
      drnasPointerX = drnasPadMotion.x();
      drnasPointerY = drnasPadMotion.y();
      drnasRefreshMouseLayer();
      drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
      drnasUpdatePointer();
    } else {
      drnasHideMouseLayer();
    }
    drnasUpdateModeBadge();
    drnasRefreshPanels();
  }

  private void drnasUpdateModeBadge() {
    if (drnasModeBadge == null) {
      return;
    }
    final boolean relative = drnasInputMode == DRNAS_MODE_RELATIVE;
    drnasModeBadge.setText(relative ? "PAD \u25CF" : "TOUCH \u25CB");
    drnasModeBadge.setContentDescription((relative
        ? DrnasLanguage.text(BlenderActivity.this,
            "Virtual touchpad mode. Tap to switch to direct touch.",
            "Nacin virtualnog touchpada. Dodirni za izravni dodir.")
        : DrnasLanguage.text(BlenderActivity.this,
            "Direct touch mode. Tap to switch to virtual touchpad.",
            "Izravni dodir. Dodirni za virtualni touchpad."))
        + DrnasLanguage.text(BlenderActivity.this,
            " Long press to show or hide the controls.",
            " Dugo drzi za prikaz ili skrivanje kontrola."));
    drnasModeBadge.setActivated(relative);
  }

  private void drnasShowGestureState(int kind, boolean active) {
    if (!active || kind < 0 || drnasModeBadge == null) {
      drnasUpdateModeBadge();
      return;
    }
    final String name = kind == DrnasGestureEngine.GESTURE_ORBIT ? "ORBIT"
        : kind == DrnasGestureEngine.GESTURE_PAN ? "PAN" : "ZOOM";
    drnasModeBadge.setText(name);
  }

  private void installDrnasModeBadge() {
    final TextView badge = new TextView(this);
    badge.setTextSize(11f);
    badge.setTextColor(drnasTextWhite);
    badge.setGravity(Gravity.CENTER);
    badge.setSingleLine(true);
    badge.setPadding(dpDrnas(8), 0, dpDrnas(8), 0);
    badge.setBackground(drnasPill(Color.argb(228, 16, 40, 60)));
    badge.setOnClickListener(v -> drnasSetInputMode(
        drnasInputMode == DRNAS_MODE_RELATIVE ? DRNAS_MODE_DIRECT : DRNAS_MODE_RELATIVE, true));
    /* The badge is the only control that stays visible when everything else is hidden, so it is
     * also the way back: long press hides or restores the rest of the overlay. */
    badge.setOnLongClickListener(v -> {
      drnasToggleOverlay();
      return true;
    });
    drnasModeBadge = badge;
    drnasRegisterSkinGroup(badge);
    drnasModeChrome = badge;
    final FrameLayout.LayoutParams location = new FrameLayout.LayoutParams(
        dpDrnas(76), dpDrnas(34), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    location.topMargin = dpDrnas(54);
    drnasAddPanel(badge, location);
    drnasSetOffsetX(badge, drnasPrefs.getFloat("mode_badge_x", 0f));
    drnasSetOffsetY(badge, drnasPrefs.getFloat("mode_badge_y", 0f));
    drnasUpdateModeBadge();
  }

  /* ------------------------------------------------------------------ trackpad */

  private static int drnasGestureAction(int actionMasked) {
    switch (actionMasked) {
      case MotionEvent.ACTION_DOWN: return DrnasGestureEngine.ACTION_DOWN;
      case MotionEvent.ACTION_MOVE: return DrnasGestureEngine.ACTION_MOVE;
      case MotionEvent.ACTION_POINTER_DOWN: return DrnasGestureEngine.ACTION_POINTER_DOWN;
      case MotionEvent.ACTION_POINTER_UP: return DrnasGestureEngine.ACTION_POINTER_UP;
      case MotionEvent.ACTION_UP: return DrnasGestureEngine.ACTION_UP;
      case MotionEvent.ACTION_CANCEL: return DrnasGestureEngine.ACTION_CANCEL;
      default: return -1;
    }
  }

  /** Physical mouse, trackball and stylus-hover input keeps behaving like a desktop mouse. */
  private boolean drnasHandleMouseEvent(android.view.MotionEvent event) {
    drnasSyncPointerGeometry();
    if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
      final float vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
      final float horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
      if (vertical != 0f) drnasSendInput(6, 0f, 0f, vertical);
      if (horizontal != 0f) drnasSendInput(12, 0f, 0f, horizontal);
      return true;
    }
    drnasMovePointerQuiet(event.getX(), event.getY());
    final int buttons = event.getActionMasked() == MotionEvent.ACTION_CANCEL
        ? 0 : event.getButtonState();
    drnasSyncPhysicalButton(DrnasMouseState.LEFT, (buttons & MotionEvent.BUTTON_PRIMARY) != 0);
    drnasSyncPhysicalButton(DrnasMouseState.RIGHT, (buttons & MotionEvent.BUTTON_SECONDARY) != 0);
    drnasSyncPhysicalButton(DrnasMouseState.MIDDLE, (buttons & MotionEvent.BUTTON_TERTIARY) != 0);
    return true;
  }

  private void drnasMovePointerQuiet(float x, float y) {
    drnasPointerX = drnasMap.nativeX(x);
    drnasPointerY = drnasMap.nativeY(y);
    if (drnasInputMode == DRNAS_MODE_RELATIVE) {
      drnasPadMotion.setPosition(drnasPointerX, drnasPointerY);
    }
    drnasSendInput(1, drnasPointerX, drnasPointerY, 0f);
  }

  private void drnasSyncPhysicalButton(int bit, boolean down) {
    if (drnasMouseButtons == null) {
      return;
    }
    if (down && !drnasMouseButtons.isHeld(bit)) {
      drnasMouseButtons.press(bit);
    } else if (!down && drnasMouseButtons.isHeld(bit)) {
      drnasMouseButtons.release(bit);
    }
  }

  private final class DrnasTrackpad implements View.OnTouchListener {
    public boolean onTouch(View v, MotionEvent event) {
      if (drnasEdit) {
        return true;
      }
      if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
        return drnasHandleMouseEvent(event);
      }
      if (drnasGestures == null) {
        return true;
      }
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN) drnasWakeSkin();
      final int action = drnasGestureAction(event.getActionMasked());
      if (action < 0) {
        return true;
      }
      final int count = event.getPointerCount();
      final int[] ids = new int[count];
      final float[] xs = new float[count];
      final float[] ys = new float[count];
      for (int i = 0; i < count; i++) {
        ids[i] = event.getPointerId(i);
        xs[i] = event.getX(i);
        ys[i] = event.getY(i);
      }
      drnasGestures.onTouch(action, ids, xs, ys, event.getActionIndex(), event.getEventTime());
      return true;
    }
  }
