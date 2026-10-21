/* DRNAS Touch Studio — complete replacement for the legacy overlay. */
  private android.widget.FrameLayout drnasStudioRoot;
  private android.content.SharedPreferences drnasStudioPrefs;
  private final java.util.ArrayList<android.view.View> drnasStudioGroups = new java.util.ArrayList<>();
  private final java.util.ArrayList<android.widget.TextView> drnasStudioGrips = new java.util.ArrayList<>();
  private android.widget.PopupWindow drnasStudioPopup;
  private android.widget.LinearLayout drnasStudioTopBar;
  private android.widget.TextView drnasStudioEditButton, drnasStudioHideButton;
  private android.widget.TextView drnasStudioLmbView, drnasStudioRmbView;
  private DrnasInputHold drnasStudioLmb, drnasStudioRmb;
  private boolean drnasStudioEditMode = false, drnasStudioHidden = false;
  private int drnasStudioScale = 100, drnasStudioOpacity = 94, drnasStudioSensitivity = 18;
  private static final int DRNAS_STUDIO_INK = android.graphics.Color.rgb(232, 237, 245);
  private static final int DRNAS_STUDIO_MUTED = android.graphics.Color.rgb(151, 163, 180);
  private static final int DRNAS_STUDIO_SURFACE = android.graphics.Color.rgb(24, 29, 38);
  private static final int DRNAS_STUDIO_SURFACE_2 = android.graphics.Color.rgb(32, 39, 50);
  private static final int DRNAS_STUDIO_ACCENT = android.graphics.Color.rgb(68, 132, 238);
  private static final int DRNAS_STUDIO_EDGE = android.graphics.Color.rgb(63, 74, 90);

  private int drnasStudioDp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private android.graphics.drawable.GradientDrawable drnasStudioShape(int fill, int edge, int radiusDp) {
    android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(drnasStudioDp(radiusDp));
    d.setStroke(drnasStudioDp(1), edge);
    return d;
  }

  private android.widget.TextView drnasStudioLabel(String text, float size, int color) {
    android.widget.TextView v = new android.widget.TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setGravity(android.view.Gravity.CENTER);
    v.setIncludeFontPadding(false);
    v.setContentDescription(text);
    return v;
  }

  private android.widget.TextView drnasStudioPill(String text, boolean primary) {
    android.widget.TextView v = drnasStudioLabel(text, 11, DRNAS_STUDIO_INK);
    v.setPadding(drnasStudioDp(10), 0, drnasStudioDp(10), 0);
    v.setMinHeight(drnasStudioDp(42));
    v.setBackground(drnasStudioShape(
        primary ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_SURFACE_2,
        primary ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_EDGE, 12));
    v.setElevation(drnasStudioDp(2));
    return v;
  }

  private android.widget.LinearLayout drnasStudioRow() {
    android.widget.LinearLayout row = new android.widget.LinearLayout(this);
    row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setClipChildren(false);
    row.setClipToPadding(false);
    return row;
  }

  private void installDrnasTouchStudio() {
    drnasStudioPrefs = getSharedPreferences("drnas_touch_studio_v1", MODE_PRIVATE);
    drnasStudioScale = drnasStudioPrefs.getInt("scale", 100);
    drnasStudioOpacity = drnasStudioPrefs.getInt("opacity", 94);
    drnasStudioSensitivity = drnasStudioPrefs.getInt("sensitivity", 18);

    drnasStudioLmb = new DrnasInputHold(held -> {
      nativeOnDrnasInput(held ? 2 : 3, 0f, 0f, held ? 1f : 0f);
      drnasStudioRefreshMouseButtons();
    }, 300);
    drnasStudioRmb = new DrnasInputHold(held -> {
      nativeOnDrnasInput(held ? 4 : 5, 0f, 0f, held ? 1f : 0f);
      drnasStudioRefreshMouseButtons();
    }, 300);
    nativeOnDrnasInput(7, 0f, 0f, Math.max(0.08f, drnasStudioSensitivity / 100f));

    drnasStudioRoot = new android.widget.FrameLayout(this);
    drnasStudioRoot.setClipChildren(false);
    drnasStudioRoot.setClipToPadding(false);
    drnasStudioRoot.setClickable(false);
    drnasStudioRoot.setFocusable(false);
    addContentView(drnasStudioRoot, new android.widget.FrameLayout.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT));

    drnasStudioBuildTopBar();
    drnasStudioBuildMouseGroup();
    drnasStudioBuildToolGroup();
    drnasStudioBuildViewGroup();
    drnasStudioApplyStyle();
  }

  private void drnasStudioBuildTopBar() {
    drnasStudioTopBar = drnasStudioRow();
    drnasStudioTopBar.setPadding(drnasStudioDp(8), drnasStudioDp(5),
        drnasStudioDp(6), drnasStudioDp(5));
    drnasStudioTopBar.setBackground(drnasStudioShape(
        android.graphics.Color.argb(244, 22, 27, 36), DRNAS_STUDIO_EDGE, 14));
    drnasStudioTopBar.setElevation(drnasStudioDp(5));

    android.widget.LinearLayout brand = new android.widget.LinearLayout(this);
    brand.setOrientation(android.widget.LinearLayout.VERTICAL);
    brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
    android.widget.TextView title = drnasStudioLabel("BLENDER", 10, DRNAS_STUDIO_INK);
    title.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    android.widget.TextView sub = drnasStudioLabel("TOUCH STUDIO", 8, DRNAS_STUDIO_MUTED);
    sub.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    brand.addView(title, new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(14)));
    brand.addView(sub, new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(12)));
    android.widget.LinearLayout.LayoutParams brandLp =
        new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(32));
    brandLp.rightMargin = drnasStudioDp(10);
    drnasStudioTopBar.addView(brand, brandLp);

    drnasStudioEditButton = drnasStudioPill("EDIT LAYOUT", false);
    drnasStudioEditButton.setMinHeight(drnasStudioDp(34));
    drnasStudioEditButton.setOnClickListener(v -> {
      drnasStudioEditMode = !drnasStudioEditMode;
      drnasStudioRefreshEditMode();
      v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    });
    drnasStudioTopBar.addView(drnasStudioEditButton,
        new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(34)));

    android.widget.TextView settings = drnasStudioPill("SETTINGS", false);
    settings.setMinHeight(drnasStudioDp(34));
    android.widget.LinearLayout.LayoutParams settingsLp =
        new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(34));
    settingsLp.leftMargin = drnasStudioDp(5);
    drnasStudioTopBar.addView(settings, settingsLp);
    settings.setOnClickListener(v -> drnasStudioShowSettings());

    drnasStudioHideButton = drnasStudioPill("HIDE", false);
    drnasStudioHideButton.setMinHeight(drnasStudioDp(34));
    android.widget.LinearLayout.LayoutParams hideLp =
        new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(34));
    hideLp.leftMargin = drnasStudioDp(5);
    drnasStudioTopBar.addView(drnasStudioHideButton, hideLp);
    drnasStudioHideButton.setOnClickListener(v -> {
      drnasStudioHidden = !drnasStudioHidden;
      for (android.view.View group : drnasStudioGroups) {
        group.setVisibility(drnasStudioHidden ? android.view.View.GONE : android.view.View.VISIBLE);
      }
      drnasStudioHideButton.setText(drnasStudioHidden ? "SHOW" : "HIDE");
    });

    android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
        -2, -2, android.view.Gravity.TOP | android.view.Gravity.LEFT);
    lp.leftMargin = drnasStudioDp(10);
    lp.topMargin = drnasStudioDp(10);
    drnasStudioRoot.addView(drnasStudioTopBar, lp);
  }

  private void drnasStudioBuildMouseGroup() {
    android.widget.LinearLayout row = drnasStudioRow();
    drnasStudioLmbView = drnasStudioPill("LMB", true);
    drnasStudioRmbView = drnasStudioPill("RMB", false);
    drnasStudioLmbView.setContentDescription("Left mouse button. Hold to drag; double-tap to lock.");
    drnasStudioRmbView.setContentDescription("Right mouse button. Hold to drag; double-tap to lock.");
    android.widget.LinearLayout.LayoutParams p =
        new android.widget.LinearLayout.LayoutParams(0, drnasStudioDp(46), 1f);
    p.rightMargin = drnasStudioDp(5);
    row.addView(drnasStudioLmbView, p);
    android.widget.LinearLayout.LayoutParams q =
        new android.widget.LinearLayout.LayoutParams(0, drnasStudioDp(46), 1f);
    row.addView(drnasStudioRmbView, q);

    drnasStudioLmbView.setOnTouchListener((v, e) ->
        drnasStudioMouseTouch(v, e, drnasStudioLmb));
    drnasStudioRmbView.setOnTouchListener((v, e) ->
        drnasStudioMouseTouch(v, e, drnasStudioRmb));

    drnasStudioAddGroup("mouse", "MOUSE", row,
        android.view.Gravity.LEFT | android.view.Gravity.BOTTOM,
        10, 0, 0, 12, 154);
  }

  private boolean drnasStudioMouseTouch(android.view.View view, android.view.MotionEvent e,
                                         DrnasInputHold hold) {
    if (drnasStudioEditMode) return true;
    switch (e.getActionMasked()) {
      case android.view.MotionEvent.ACTION_DOWN:
        view.setAlpha(0.72f);
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        hold.down(android.os.SystemClock.uptimeMillis());
        return true;
      case android.view.MotionEvent.ACTION_MOVE:
        return true;
      case android.view.MotionEvent.ACTION_UP:
        view.setAlpha(1f);
        hold.up(android.os.SystemClock.uptimeMillis(), true);
        return true;
      case android.view.MotionEvent.ACTION_CANCEL:
        view.setAlpha(1f);
        hold.cancel();
        return true;
      default:
        return true;
    }
  }

  private void drnasStudioRefreshMouseButtons() {
    if (drnasStudioLmbView != null && drnasStudioLmb != null) {
      boolean active = drnasStudioLmb.isHeld();
      drnasStudioLmbView.setText(drnasStudioLmb.isLatched() ? "LMB  •  LOCK" : "LMB");
      drnasStudioLmbView.setBackground(drnasStudioShape(
          active ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_SURFACE_2,
          active ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_EDGE, 12));
    }
    if (drnasStudioRmbView != null && drnasStudioRmb != null) {
      boolean active = drnasStudioRmb.isHeld();
      drnasStudioRmbView.setText(drnasStudioRmb.isLatched() ? "RMB  •  LOCK" : "RMB");
      drnasStudioRmbView.setBackground(drnasStudioShape(
          active ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_SURFACE_2,
          active ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_EDGE, 12));
    }
  }

  private void drnasStudioBuildToolGroup() {
    android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
    scroller.setHorizontalScrollBarEnabled(false);
    scroller.setFillViewport(false);
    scroller.setClipChildren(false);
    scroller.setClipToPadding(false);
    android.widget.LinearLayout row = drnasStudioRow();
    drnasStudioAddTool(row, "MOVE", "G", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_G, 0), true);
    drnasStudioAddTool(row, "ROTATE", "R", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_R, 0), false);
    drnasStudioAddTool(row, "SCALE", "S", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_S, 0), false);
    drnasStudioAddTool(row, "TAB", "MODE", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_TAB, 0), false);
    drnasStudioAddTool(row, "CANCEL", "ESC", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_ESCAPE, 0), false);
    drnasStudioAddTool(row, "UNDO", "CTRL Z", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.META_CTRL_ON), false);
    drnasStudioAddTool(row, "REDO", "CTRL ⇧ Z", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_Z,
        android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_SHIFT_ON), false);
    drnasStudioAddTool(row, "KEYS", "⌨", () -> drnasStudioShowKeys(), false);
    scroller.addView(row, new android.widget.HorizontalScrollView.LayoutParams(-2, drnasStudioDp(48)));
    int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels /
        getResources().getDisplayMetrics().density);
    int rowWidth = Math.max(180, Math.min(500, screenDp - 154 - 104 - 36));
    drnasStudioAddGroup("tools", "TOOLS", scroller,
        android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM,
        0, 0, 0, 12, rowWidth);
  }

  private void drnasStudioAddTool(android.widget.LinearLayout row, String title, String key,
                                  Runnable action, boolean primary) {
    android.widget.LinearLayout item = new android.widget.LinearLayout(this);
    item.setOrientation(android.widget.LinearLayout.VERTICAL);
    item.setGravity(android.view.Gravity.CENTER);
    item.setPadding(drnasStudioDp(9), 0, drnasStudioDp(9), 0);
    item.setBackground(drnasStudioShape(
        primary ? android.graphics.Color.rgb(38, 77, 130) : DRNAS_STUDIO_SURFACE_2,
        primary ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_EDGE, 10));
    item.setElevation(drnasStudioDp(2));
    android.widget.TextView main = drnasStudioLabel(title, 9, DRNAS_STUDIO_INK);
    main.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    android.widget.TextView sub = drnasStudioLabel(key, 8, primary
        ? android.graphics.Color.rgb(194, 218, 255) : DRNAS_STUDIO_MUTED);
    item.addView(main, new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(17)));
    item.addView(sub, new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(13)));
    android.widget.LinearLayout.LayoutParams p =
        new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(44));
    p.rightMargin = drnasStudioDp(5);
    row.addView(item, p);
    item.setContentDescription(title + " " + key);
    item.setOnClickListener(v -> {
      v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
      action.run();
    });
  }

  private void drnasStudioBuildViewGroup() {
    android.widget.LinearLayout row = drnasStudioRow();
    android.widget.TextView zoomIn = drnasStudioPill("+", false);
    android.widget.TextView zoomOut = drnasStudioPill("−", false);
    android.widget.TextView view = drnasStudioPill("VIEW", true);
    zoomIn.setMinWidth(drnasStudioDp(30));
    zoomOut.setMinWidth(drnasStudioDp(30));
    zoomIn.setPadding(0, 0, 0, 0);
    zoomOut.setPadding(0, 0, 0, 0);
    view.setPadding(drnasStudioDp(7), 0, drnasStudioDp(7), 0);
    android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(44));
    p.rightMargin = drnasStudioDp(4);
    row.addView(zoomIn, p);
    android.widget.LinearLayout.LayoutParams q = new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(44));
    q.rightMargin = drnasStudioDp(4);
    row.addView(zoomOut, q);
    row.addView(view, new android.widget.LinearLayout.LayoutParams(-2, drnasStudioDp(44)));
    zoomIn.setOnClickListener(v -> nativeOnDrnasInput(6, 0f, 0f, 1f));
    zoomOut.setOnClickListener(v -> nativeOnDrnasInput(6, 0f, 0f, -1f));
    view.setOnClickListener(v -> drnasStudioShowViews());
    drnasStudioAddGroup("view", "VIEWPORT", row,
        android.view.Gravity.RIGHT | android.view.Gravity.BOTTOM,
        0, 0, 10, 12, 104);
  }

  private void drnasStudioAddGroup(String key, String title, android.view.View content,
      int gravity, int left, int top, int right, int bottom, int widthDp) {
    android.widget.LinearLayout group = new android.widget.LinearLayout(this);
    group.setOrientation(android.widget.LinearLayout.VERTICAL);
    group.setPadding(drnasStudioDp(7), drnasStudioDp(5), drnasStudioDp(7), drnasStudioDp(7));
    group.setClipChildren(false);
    group.setClipToPadding(false);
    group.setBackground(drnasStudioShape(
        android.graphics.Color.argb(238, 24, 29, 38), DRNAS_STUDIO_EDGE, 15));
    group.setElevation(drnasStudioDp(5));

    android.widget.TextView grip = drnasStudioLabel("⋮⋮  " + title, 8, DRNAS_STUDIO_MUTED);
    grip.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    grip.setPadding(drnasStudioDp(3), 0, drnasStudioDp(3), drnasStudioDp(2));
    grip.setMinHeight(drnasStudioDp(19));
    grip.setTag(title);
    group.addView(grip, new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(19)));
    group.addView(content, new android.widget.LinearLayout.LayoutParams(-1, -2));

    android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
        drnasStudioDp(widthDp), -2, gravity);
    lp.setMargins(drnasStudioDp(left), drnasStudioDp(top), drnasStudioDp(right), drnasStudioDp(bottom));
    drnasStudioRoot.addView(group, lp);
    group.setTranslationX(drnasStudioPrefs.getFloat(key + "_x", 0f));
    group.setTranslationY(drnasStudioPrefs.getFloat(key + "_y", 0f));
    grip.setOnTouchListener((v, e) -> drnasStudioDragGroup(v, group, key, e));
    group.setTag(key);
    drnasStudioGroups.add(group);
    drnasStudioGrips.add(grip);
  }

  private boolean drnasStudioDragGroup(android.view.View grip, android.view.View group,
                                        String key, android.view.MotionEvent e) {
    if (!drnasStudioEditMode) return false;
    switch (e.getActionMasked()) {
      case android.view.MotionEvent.ACTION_DOWN:
        grip.setAlpha(1f);
        grip.setTextColor(DRNAS_STUDIO_INK);
        grip.setTag(new Object[] {grip.getTag(), e.getRawX(), e.getRawY()});
        return true;
      case android.view.MotionEvent.ACTION_MOVE: {
        Object tag = grip.getTag();
        if (!(tag instanceof Object[])) return true;
        Object[] state = (Object[]) tag;
        float oldX = ((Number) state[1]).floatValue();
        float oldY = ((Number) state[2]).floatValue();
        float dx = e.getRawX() - oldX, dy = e.getRawY() - oldY;
        float rootW = drnasStudioRoot.getWidth() > 0 ? drnasStudioRoot.getWidth()
            : getResources().getDisplayMetrics().widthPixels;
        float rootH = drnasStudioRoot.getHeight() > 0 ? drnasStudioRoot.getHeight()
            : getResources().getDisplayMetrics().heightPixels;
        float newX = Math.max(drnasStudioDp(4), Math.min(rootW - group.getWidth() - drnasStudioDp(4), group.getX() + dx));
        float newY = Math.max(drnasStudioDp(4), Math.min(rootH - group.getHeight() - drnasStudioDp(4), group.getY() + dy));
        group.setTranslationX(newX - group.getLeft());
        group.setTranslationY(newY - group.getTop());
        state[1] = e.getRawX();
        state[2] = e.getRawY();
        return true;
      }
      case android.view.MotionEvent.ACTION_UP: {
        drnasStudioPrefs.edit().putFloat(key + "_x", group.getTranslationX())
            .putFloat(key + "_y", group.getTranslationY()).apply();
        grip.setTag(((Object[]) grip.getTag())[0]);
        drnasStudioApplyStyle();
        return true;
      }
      case android.view.MotionEvent.ACTION_CANCEL:
        Object tag = grip.getTag();
        if (tag instanceof Object[]) grip.setTag(((Object[]) tag)[0]);
        return true;
      default:
        return true;
    }
  }

  private void drnasStudioRefreshEditMode() {
    drnasStudioEditButton.setText(drnasStudioEditMode ? "DONE" : "EDIT LAYOUT");
    for (int i = 0; i < drnasStudioGroups.size(); i++) {
      android.view.View g = drnasStudioGroups.get(i);
      android.widget.TextView grip = drnasStudioGrips.get(i);
      grip.setText((drnasStudioEditMode ? "↕  DRAG  •  " : "⋮⋮  ") + grip.getTag().toString());
      g.setBackground(drnasStudioShape(android.graphics.Color.argb(238, 24, 29, 38),
          drnasStudioEditMode ? DRNAS_STUDIO_ACCENT : DRNAS_STUDIO_EDGE, 15));
    }
  }

  private void drnasStudioApplyStyle() {
    float scale = Math.max(0.75f, Math.min(1.30f, drnasStudioScale / 100f));
    float alpha = Math.max(0.65f, Math.min(1f, drnasStudioOpacity / 100f));
    for (android.view.View g : drnasStudioGroups) {
      g.setScaleX(scale);
      g.setScaleY(scale);
      g.setAlpha(alpha);
    }
    if (drnasStudioTopBar != null) drnasStudioTopBar.setAlpha(Math.max(0.80f, alpha));
  }

  private void drnasStudioKey(int androidKey, int meta) {
    nativeOnKey(androidKey, android.view.KeyEvent.ACTION_DOWN, meta);
    nativeOnKey(androidKey, android.view.KeyEvent.ACTION_UP, meta);
  }

  private android.widget.LinearLayout drnasStudioPopupRoot(String title) {
    android.widget.LinearLayout root = new android.widget.LinearLayout(this);
    root.setOrientation(android.widget.LinearLayout.VERTICAL);
    root.setPadding(drnasStudioDp(12), drnasStudioDp(10), drnasStudioDp(12), drnasStudioDp(12));
    root.setBackground(drnasStudioShape(android.graphics.Color.rgb(18, 23, 31), DRNAS_STUDIO_EDGE, 17));
    android.widget.TextView heading = drnasStudioLabel(title, 12, DRNAS_STUDIO_INK);
    heading.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    heading.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    root.addView(heading, new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(30)));
    return root;
  }

  private void drnasStudioShowPopup(android.widget.LinearLayout content, int heightDp) {
    if (drnasStudioPopup != null && drnasStudioPopup.isShowing()) drnasStudioPopup.dismiss();
    int screenDp = Math.round(getResources().getDisplayMetrics().widthPixels /
        getResources().getDisplayMetrics().density);
    int width = drnasStudioDp(Math.min(340, Math.max(240, screenDp - 24)));
    int height = drnasStudioDp(heightDp);
    drnasStudioPopup = new android.widget.PopupWindow(content, width, height, true);
    drnasStudioPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    drnasStudioPopup.setOutsideTouchable(true);
    drnasStudioPopup.setElevation(drnasStudioDp(12));
    drnasStudioPopup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED);
    drnasStudioPopup.showAtLocation(drnasStudioRoot,
        android.view.Gravity.TOP | android.view.Gravity.RIGHT, drnasStudioDp(12), drnasStudioDp(58));
  }

  private void drnasStudioAddPopupButton(android.widget.LinearLayout parent, String label, Runnable action) {
    android.widget.TextView b = drnasStudioPill(label, false);
    android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(42));
    lp.topMargin = drnasStudioDp(5);
    parent.addView(b, lp);
    b.setOnClickListener(v -> {
      v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
      action.run();
      if (drnasStudioPopup != null) drnasStudioPopup.dismiss();
    });
  }

  private void drnasStudioShowViews() {
    android.widget.LinearLayout root = drnasStudioPopupRoot("VIEW NAVIGATION");
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
    rows.setOrientation(android.widget.LinearLayout.VERTICAL);
    scroll.addView(rows);
    drnasStudioAddPopupButton(rows, "FRONT  •  NUMPAD 1", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_1, 0));
    drnasStudioAddPopupButton(rows, "RIGHT  •  NUMPAD 3", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_3, 0));
    drnasStudioAddPopupButton(rows, "TOP  •  NUMPAD 7", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_7, 0));
    drnasStudioAddPopupButton(rows, "PERSPECTIVE / ORTHO  •  NUMPAD 5", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_5, 0));
    drnasStudioAddPopupButton(rows, "ORBIT LEFT  •  NUMPAD 4", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_4, 0));
    drnasStudioAddPopupButton(rows, "ORBIT RIGHT  •  NUMPAD 6", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_6, 0));
    drnasStudioAddPopupButton(rows, "ORBIT UP  •  NUMPAD 8", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_8, 0));
    drnasStudioAddPopupButton(rows, "ORBIT DOWN  •  NUMPAD 2", () -> drnasStudioKey(android.view.KeyEvent.KEYCODE_NUMPAD_2, 0));
    root.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
    drnasStudioShowPopup(root, 390);
  }

  private void drnasStudioShowKeys() {
    android.widget.LinearLayout root = drnasStudioPopupRoot("QUICK KEYS");
    String[] labels = {"G", "R", "S", "X", "Y", "Z", "TAB", "ESC", "ENTER", "DEL"};
    int[] keys = {android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_R,
        android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_Y,
        android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_TAB, android.view.KeyEvent.KEYCODE_ESCAPE,
        android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DEL};
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    android.widget.LinearLayout rows = new android.widget.LinearLayout(this);
    rows.setOrientation(android.widget.LinearLayout.VERTICAL);
    for (int start = 0; start < labels.length; start += 2) {
      android.widget.LinearLayout pair = drnasStudioRow();
      for (int j = start; j < Math.min(start + 2, labels.length); j++) {
        final int key = keys[j];
        android.widget.TextView b = drnasStudioPill(labels[j], j < 3);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, drnasStudioDp(46), 1f);
        lp.setMargins(drnasStudioDp(3), drnasStudioDp(3), drnasStudioDp(3), drnasStudioDp(3));
        pair.addView(b, lp);
        b.setOnClickListener(v -> { drnasStudioKey(key, 0); if (drnasStudioPopup != null) drnasStudioPopup.dismiss(); });
      }
      rows.addView(pair);
    }
    scroll.addView(rows);
    root.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 0, 1f));
    android.widget.TextView systemKeyboard = drnasStudioPill("OPEN ANDROID KEYBOARD", false);
    root.addView(systemKeyboard, new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(42)));
    systemKeyboard.setOnClickListener(v -> {
      if (inputView != null) {
        inputView.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(inputView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
      }
      if (drnasStudioPopup != null) drnasStudioPopup.dismiss();
    });
    drnasStudioShowPopup(root, 300);
  }

  private void drnasStudioSlider(android.widget.LinearLayout parent, String title,
      int min, int max, int initial, java.util.function.IntConsumer changed) {
    android.widget.TextView label = drnasStudioLabel(title + "  " + initial, 10, DRNAS_STUDIO_MUTED);
    label.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    android.widget.LinearLayout.LayoutParams labelLp = new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(25));
    labelLp.topMargin = drnasStudioDp(4);
    parent.addView(label, labelLp);
    android.widget.SeekBar seek = new android.widget.SeekBar(this);
    seek.setMax(max - min);
    seek.setProgress(Math.max(0, Math.min(max - min, initial - min)));
    parent.addView(seek, new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(38)));
    seek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(android.widget.SeekBar b) {}
      public void onStopTrackingTouch(android.widget.SeekBar b) {}
      public void onProgressChanged(android.widget.SeekBar b, int p, boolean user) {
        int value = p + min;
        label.setText(title + "  " + value);
        if (user) changed.accept(value);
      }
    });
  }

  private void drnasStudioShowSettings() {
    android.widget.LinearLayout root = drnasStudioPopupRoot("CONTROL SETTINGS");
    android.widget.TextView hint = drnasStudioLabel(
        "Viewport touches stay native. Hold a mouse button, then drag in the canvas.",
        9, DRNAS_STUDIO_MUTED);
    hint.setGravity(android.view.Gravity.LEFT | android.view.Gravity.CENTER_VERTICAL);
    hint.setPadding(0, drnasStudioDp(4), 0, drnasStudioDp(4));
    root.addView(hint);
    drnasStudioSlider(root, "CONTROL SIZE %", 75, 130, drnasStudioScale, value -> {
      drnasStudioScale = value;
      drnasStudioPrefs.edit().putInt("scale", value).apply();
      drnasStudioApplyStyle();
    });
    drnasStudioSlider(root, "PANEL OPACITY %", 65, 100, drnasStudioOpacity, value -> {
      drnasStudioOpacity = value;
      drnasStudioPrefs.edit().putInt("opacity", value).apply();
      drnasStudioApplyStyle();
    });
    drnasStudioSlider(root, "GESTURE SENSITIVITY %", 8, 50, drnasStudioSensitivity, value -> {
      drnasStudioSensitivity = value;
      drnasStudioPrefs.edit().putInt("sensitivity", value).apply();
      nativeOnDrnasInput(7, 0f, 0f, value / 100f);
    });
    android.widget.TextView reset = drnasStudioPill("RESET CONTROL POSITIONS", false);
    android.widget.LinearLayout.LayoutParams resetLp = new android.widget.LinearLayout.LayoutParams(-1, drnasStudioDp(42));
    resetLp.topMargin = drnasStudioDp(10);
    root.addView(reset, resetLp);
    reset.setOnClickListener(v -> {
      drnasStudioPrefs.edit().remove("mouse_x").remove("mouse_y")
          .remove("tools_x").remove("tools_y").remove("view_x").remove("view_y").apply();
      for (android.view.View g : drnasStudioGroups) { g.setTranslationX(0); g.setTranslationY(0); }
      if (drnasStudioPopup != null) drnasStudioPopup.dismiss();
    });
    drnasStudioShowPopup(root, 330);
  }

  private void drnasStudioReleaseAll() {
    if (drnasStudioLmb != null) drnasStudioLmb.cancel();
    if (drnasStudioRmb != null) drnasStudioRmb.cancel();
  }
