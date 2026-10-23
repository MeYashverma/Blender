import org.blender.blender.DrnasGestureEngine;
import java.util.ArrayList;

/**
 * Drives the gesture recogniser through complete finger sequences and asserts the emitted mouse
 * input. No Android device or SDK is needed, which is what makes the input contract testable.
 */
public class DrnasGestureEngineTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static final class Recorder implements DrnasGestureEngine.Sink {
    final ArrayList<String> events = new ArrayList<>();
    public void pointer(float x, float y, float dx, float dy) {
      events.add("p" + Math.round(x) + ":" + Math.round(y));
    }
    public void button(int button, boolean down) {
      events.add("b" + button + (down ? "+" : "-"));
    }
    public void wheel(int axis, float amount) {
      events.add("w" + axis + ":" + Math.round(amount));
    }
    public void modifier(int modifier, boolean down) {
      events.add("m" + modifier + (down ? "+" : "-"));
    }
    public void gesture(int kind, boolean active) {
      events.add("g" + kind + (active ? "+" : "-"));
    }
    int count(String event) {
      int total = 0;
      for (String recorded : events) if (recorded.equals(event)) total++;
      return total;
    }
    int indexOf(String event) {
      return events.indexOf(event);
    }
    int totalWheel(int axis) {
      int total = 0;
      String prefix = "w" + axis + ":";
      for (String recorded : events) {
        if (recorded.startsWith(prefix)) total += Integer.parseInt(recorded.substring(prefix.length()));
      }
      return total;
    }
    boolean hasButton(int button) {
      return count("b" + button + "+") > 0 || count("b" + button + "-") > 0;
    }
    String last() { return events.isEmpty() ? "" : events.get(events.size() - 1); }
  }

  private static int[] ids(int... values) { return values; }
  private static float[] coords(float... values) { return values; }

  private static final DrnasGestureEngine.Sink NO_SINK = new DrnasGestureEngine.Sink() {
    public void pointer(float x, float y, float dx, float dy) {}
    public void button(int button, boolean down) {}
    public void wheel(int axis, float amount) {}
    public void modifier(int modifier, boolean down) {}
    public void gesture(int kind, boolean active) {}
  };

  public static void main(String[] args) {
    // ---------------------------------------------------------------- one finger: tap
    Recorder rec = new Recorder();
    DrnasGestureEngine engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(100), 0, 1080);
    check(rec.count("b1+") == 1 && rec.count("b1-") == 1, "a tap is one real left click");
    check(rec.events.get(rec.events.size() - 2).equals("b1+"), "click press then release: " + rec.events);
    check(!rec.hasButton(2) && !rec.hasButton(3), "a tap must not touch other buttons");
    check(engine.activeGesture() == -1, "no gesture left over after a tap");

    // A tap with drift smaller than the slop is still a click, and it clicks where it landed.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(105), coords(101), 0, 1040);
    check(!rec.hasButton(1), "movement inside the slop is not a press yet");
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(105), coords(101), 0, 1080);
    int pressAt = rec.events.indexOf("b1+");
    check(pressAt > 0 && rec.events.get(pressAt - 1).equals("p100:100"),
        "click happens at the landing point: " + rec.events);

    // ---------------------------------------------------------------- one finger: drag
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(110), coords(100), 0, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(140), coords(100), 0, 1040);
    check(rec.count("b1+") == 1, "a drag is one left press");
    int dragPress = rec.indexOf("b1+");
    check(dragPress > 0 && rec.events.get(dragPress - 1).equals("p100:100"),
        "the press is reported where the finger landed, not where it travelled to: " + rec.events);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(140), coords(100), 0, 1080);
    check(rec.count("b1-") == 1 && !rec.hasButton(2), "drag releases the left button once");
    check(rec.count("b1+") == rec.count("b1-"), "presses and releases are balanced");

    // ---------------------------------------------------------------- cancel / lifecycle
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(300), coords(100), 0, 1040);
    engine.onTouch(DrnasGestureEngine.ACTION_CANCEL, ids(0), coords(300), coords(100), 0, 1050);
    check(rec.count("b1+") == 1 && rec.count("b1-") == 1, "cancel releases the drag");
    check(!engine.isLeftDown(), "engine knows the button is up");
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(300), coords(100), 0, 1060);
    check(rec.count("b1+") == 1, "a cancel must never turn into a click");

    // A cancel without a press produces no click at all.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(10), coords(10), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_CANCEL, ids(0), coords(10), coords(10), 0, 1010);
    check(!rec.hasButton(1), "cancelled tap does not click");

    // ---------------------------------------------------------------- two fingers: orbit
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(140, 140), 0, 1040);
    check(rec.count("b3+") == 1, "two-finger drag presses the middle button once");
    check(rec.count("b1+") == 0, "two-finger drag must not click the left button");
    check(rec.count("g" + DrnasGestureEngine.GESTURE_ORBIT + "+") == 1, "orbit is reported to the UI");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(180, 180), 0, 1060);
    check(rec.count("b3+") == 1, "moving again must not re-press the middle button");
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(100, 200), coords(180, 180), 1, 1080);
    check(rec.count("b3-") == 1, "lifting one finger releases the middle button");
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(180), 0, 1090);
    check(rec.count("b1+") == 0, "the end of an orbit must not click");

    // Pointer ids, not indices: an Android reorder mid-gesture must not move or click anything.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(5), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(5, 2),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(2, 5),
        coords(200, 100), coords(160, 160), 0, 1040);
    check(rec.count("b3+") == 1, "re-ordered pointer ids still orbit");
    engine.releaseAll("test");

    // ---------------------------------------------------------------- two fingers: pinch
    // Notch sizes are exact powers of two here so the expected wheel totals are exact too.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setZoomNotchesPerPixel(0.125f);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(88, 212), coords(100, 100), 0, 1040);
    check(rec.count("b3+") == 0, "a pinch must not press the middle button");
    check(rec.count("g" + DrnasGestureEngine.GESTURE_ZOOM + "+") == 1, "zoom is reported");
    check(rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL) == 0,
        "the classifying move itself must not jump the zoom");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(84, 216), coords(100, 100), 0, 1060);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(80, 220), coords(100, 100), 0, 1065);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(76, 224), coords(100, 100), 0, 1070);
    check(rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL) == 3,
        "24px of extra spread is three notches at 0.125 notch/px, got "
            + rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL));
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(76, 224), coords(100, 100), 1, 1080);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(76), coords(100), 0, 1090);
    check(rec.count("b1+") == 0 && rec.count("b2+") == 0, "a pinch never clicks");

    // Pinching in zooms the other way, and never scrolls the wrong direction.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setZoomNotchesPerPixel(0.125f);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(88), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(88, 212), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(100, 100), 0, 1040);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(104, 196), coords(100, 100), 0, 1050);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(108, 192), coords(100, 100), 0, 1060);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(112, 188), coords(100, 100), 0, 1070);
    check(rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL) == -3,
        "pinching in zooms out: " + rec.events);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(112, 188), coords(100, 100), 1, 1080);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(112), coords(100), 0, 1090);

    // Sub-notch spread accumulates and is emitted as whole notches only.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setZoomNotchesPerPixel(0.125f);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 224), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 248), coords(100, 100), 0, 1030);
    for (int i = 1; i <= 8; i++) {
      engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
          coords(100, 248 + i), coords(100, 100), 0, 1030 + i);
    }
    check(rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL) == 1,
        "eight 0.125-notch moves add up to exactly one notch: "
            + rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL));
    check(rec.count("w0:1") == 1, "only whole notches are sent: " + rec.events);

    // ---------------------------------------------------------------- two-finger tap
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1060);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(100), 0, 1080);
    check(rec.count("b2+") == 1 && rec.count("b2-") == 1,
        "two-finger tap is a right click: " + rec.events);
    check(rec.count("b1+") == 0, "two-finger tap must not left click");

    // A slow two-finger hold is not a tap.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 3000);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(100), 0, 3010);
    check(rec.count("b2+") == 0, "a long two-finger hold is not a right click");

    // ---------------------------------------------------------------- three fingers: pan
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1010);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1, 2),
        coords(100, 200, 300), coords(100, 100, 100), 2, 1020);
    check(rec.count("b3+") == 1, "three-finger pan presses the middle button");
    check(rec.count("m" + DrnasGestureEngine.MODIFIER_SHIFT + "+") == 1,
        "three-finger pan holds Shift");
    check(rec.indexOf("m1+") < rec.indexOf("b3+"),
        "Shift must be down before the middle button: " + rec.events);
    check(rec.count("b1+") == 0, "pan does not click");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1, 2),
        coords(100, 200, 300), coords(160, 160, 160), 0, 1040);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1, 2),
        coords(100, 200, 300), coords(160, 160, 160), 2, 1060);
    check(rec.count("b3-") == 1 && rec.count("m1-") == 1,
        "pan releases middle and Shift: " + rec.events);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(100, 200), coords(160, 160), 1, 1070);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(160), 0, 1080);
    check(rec.count("b1+") == 0 && rec.count("b2+") == 0, "ending a pan never clicks");

    // ---------------------------------------------------------------- drag is never stolen
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(160), coords(100), 0, 1020);
    check(rec.count("b1+") == 1, "drag started");
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(160, 260), coords(100, 100), 1, 1040);
    check(rec.count("b3+") == 0, "a second finger must not interrupt an object drag");
    check(engine.isLeftDown(), "left button is still held while dragging");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(180, 280), coords(100, 100), 0, 1060);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(180, 280), coords(100, 100), 1, 1080);
    check(rec.count("b1-") == 0 && engine.isLeftDown(),
        "the drag finger still holds: a stray finger lifting must not abort the transform");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(200), coords(100), 0, 1085);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(200), coords(100), 0, 1090);
    check(rec.count("b1-") == 1 && rec.count("b3+") == 0,
        "the drag ends with exactly one release when its own finger lifts, and never orbits");

    // ---------------------------------------------------------------- configuration
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setTwoFingerOrbit(false);
    engine.setZoomNotchesPerPixel(0.125f);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1020);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(140, 140), 0, 1040);
    check(rec.count("b3+") == 0, "orbit disabled: no middle button");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(240, 240), 0, 1060);
    check(rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL) == -4,
        "orbit disabled falls back to scrolling, got "
            + rec.totalWheel(DrnasGestureEngine.WHEEL_VERTICAL));

    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setThreeFingerPan(false);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1010);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1, 2),
        coords(100, 200, 300), coords(100, 100, 100), 2, 1020);
    check(rec.count("b3+") == 0 && rec.count("m1+") == 0, "pan disabled does nothing");

    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.setTwoFingerTapRightClick(false);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1010);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1030);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(100), 0, 1040);
    check(rec.count("b2+") == 0, "right-click-on-two-finger-tap can be turned off");

    // ---------------------------------------------------------------- lifecycle release
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(100, 200), coords(100, 100), 1, 1010);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0, 1),
        coords(100, 200), coords(160, 160), 0, 1030);
    check(engine.isMiddleDown(), "orbit is holding the middle button");
    engine.releaseAll("pause");
    check(rec.count("b3+") == rec.count("b3-"), "pause releases the middle button");
    check(!engine.isMiddleDown() && engine.activeGesture() == -1, "state is clean after release");
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(160), 0, 1100);
    check(rec.count("b3+") == rec.count("b3-") && rec.count("b1+") == 0,
        "the stale UP after a pause does nothing");

    // A new gesture after a cancel starts clean (no stale down time, no stale tap).
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1000);
    engine.onTouch(DrnasGestureEngine.ACTION_CANCEL, ids(0), coords(100), coords(100), 0, 1010);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 1200);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(100), coords(100), 0, 1260);
    check(rec.count("b1+") == 1 && rec.count("b1-") == 1, "the next tap still works");


    // ------------------------------------------------- a stray finger must not kill a drag
    // Real case: the user is moving an object, brushes the screen with a second finger and lifts
    // it again. Android delivers POINTER_DOWN then POINTER_UP; the left button must stay down.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 2000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(160), coords(100), 0, 2050);
    check(rec.count("b1+") == 1, "the drag starts: " + rec.events);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(160, 300), coords(100, 300), 1, 2060);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(160, 300), coords(100, 300), 1, 2080);
    check(rec.count("b1-") == 0, "a secondary finger lifting must not release the drag");
    check(engine.isLeftDown(), "the drag is still held");
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(200), coords(100), 0, 2100);
    check(rec.count("b1-") == 0, "the drag continues");
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(0), coords(200), coords(100), 0, 2150);
    check(rec.count("b1+") == rec.count("b1-"), "the drag ends exactly once: " + rec.events);

    // The finger that owns the drag lifting does end it, even with another finger still down.
    rec = new Recorder();
    engine = new DrnasGestureEngine(rec);
    engine.onTouch(DrnasGestureEngine.ACTION_DOWN, ids(0), coords(100), coords(100), 0, 3000);
    engine.onTouch(DrnasGestureEngine.ACTION_MOVE, ids(0), coords(160), coords(100), 0, 3050);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_DOWN, ids(0, 1),
        coords(160, 300), coords(100, 300), 1, 3060);
    engine.onTouch(DrnasGestureEngine.ACTION_POINTER_UP, ids(0, 1),
        coords(160, 300), coords(100, 300), 0, 3080);
    check(rec.count("b1-") == 1 && !engine.isLeftDown(),
        "lifting the drag finger releases the button: " + rec.events);
    engine.onTouch(DrnasGestureEngine.ACTION_UP, ids(1), coords(300), coords(300), 0, 3100);
    check(rec.count("b1+") == rec.count("b1-"), "no second release is emitted");

    // Degenerate input must not throw (an event with no pointers, a null batch).
    DrnasGestureEngine quiet = new DrnasGestureEngine(NO_SINK);
    quiet.onTouch(DrnasGestureEngine.ACTION_MOVE, new int[0], new float[0], new float[0], 0, 0);
    quiet.onTouch(DrnasGestureEngine.ACTION_DOWN, null, null, null, 0, 0);
    quiet.releaseAll("empty");

    System.out.println("PASS: tap, slop, drag, cancel, orbit, pinch, wheel remainders, "
        + "two-finger tap, pan, drag safety, stray-finger drag continuity, configuration, "
        + "lifecycle release and degenerate input");
  }
}
