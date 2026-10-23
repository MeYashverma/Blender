import org.blender.blender.DrnasMousePadGesture;
import java.util.ArrayList;

public final class DrnasMousePadGestureTest {
  private static final class Recorder implements DrnasMousePadGesture.Sink {
    final ArrayList<String> events = new ArrayList<>();
    public void pointer(float dx, float dy) { events.add("P:" + dx + "," + dy); }
    public void wheel(float v, float h) { events.add("W:" + v + "," + h); }
    public void click(int button) { events.add("C:" + button); }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void event(DrnasMousePadGesture g, int action, int actionIndex, long time,
                            int[] ids, float[] xs, float[] ys) {
    g.onTouch(action, ids, xs, ys, actionIndex, time);
  }

  public static void main(String[] args) {
    Recorder r = new Recorder();
    DrnasMousePadGesture g = new DrnasMousePadGesture(r);
    g.configure(32, 30, 8, 320);

    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 10,
        new int[] {41}, new float[] {20}, new float[] {20});
    event(g, DrnasMousePadGesture.ACTION_UP, 0, 90,
        new int[] {41}, new float[] {20}, new float[] {20});
    check(r.events.toString().equals("[C:1]"), "one-finger tap is LMB: " + r.events);

    r.events.clear();
    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 200,
        new int[] {5}, new float[] {10}, new float[] {10});
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 230,
        new int[] {5}, new float[] {34}, new float[] {15});
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 240,
        new int[] {5}, new float[] {40}, new float[] {21});
    event(g, DrnasMousePadGesture.ACTION_UP, 0, 250,
        new int[] {5}, new float[] {40}, new float[] {21});
    check(r.events.size() == 2 && r.events.get(0).startsWith("P:")
        && r.events.get(1).startsWith("P:"), "one-finger drag moves, never clicks: " + r.events);

    r.events.clear();
    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 300,
        new int[] {1}, new float[] {-10}, new float[] {0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_DOWN, 1, 330,
        new int[] {1, 8}, new float[] {-10, 10}, new float[] {0, 0});
    // MotionEvent pointer order can change. Stable IDs must prevent a jump or false pinch.
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 350,
        new int[] {8, 1}, new float[] {10, -10}, new float[] {0, 0});
    check(r.events.isEmpty(), "pointer-index reorder does not create motion: " + r.events);
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 380,
        new int[] {8, 1}, new float[] {10, -10}, new float[] {-48, -48});
    check(r.events.size() == 1 && r.events.get(0).startsWith("W:")
        && Float.parseFloat(r.events.get(0).substring(2, r.events.get(0).indexOf(','))) > 0f,
        "two-finger upward motion scrolls up: " + r.events);
    event(g, DrnasMousePadGesture.ACTION_POINTER_UP, 0, 390,
        new int[] {8, 1}, new float[] {10, -10}, new float[] {-48, -48});
    event(g, DrnasMousePadGesture.ACTION_UP, 0, 400,
        new int[] {1}, new float[] {-10}, new float[] {-48});
    check(r.events.size() == 1, "a scroll cannot also click: " + r.events);

    r.events.clear();
    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 500,
        new int[] {2}, new float[] {-12}, new float[] {0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_DOWN, 1, 520,
        new int[] {2, 3}, new float[] {-12, 12}, new float[] {0, 0});
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 550,
        new int[] {2, 3}, new float[] {-38, 38}, new float[] {0, 0});
    check(r.events.size() == 1 && r.events.get(0).startsWith("W:")
        && Float.parseFloat(r.events.get(0).substring(2, r.events.get(0).indexOf(','))) > 0f,
        "pinch-out emits zoom-in wheel: " + r.events);
    event(g, DrnasMousePadGesture.ACTION_CANCEL, 0, 560,
        new int[] {2, 3}, new float[] {-38, 38}, new float[] {0, 0});
    check(g.activePointerCount() == 0, "cancel releases all tracked pointers");

    r.events.clear();
    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 600,
        new int[] {11}, new float[] {-12}, new float[] {0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_DOWN, 1, 620,
        new int[] {11, 13}, new float[] {-12, 12}, new float[] {0, 0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_UP, 1, 650,
        new int[] {11, 13}, new float[] {-12, 12}, new float[] {0, 0});
    event(g, DrnasMousePadGesture.ACTION_UP, 0, 670,
        new int[] {11}, new float[] {-12}, new float[] {0});
    check(r.events.toString().equals("[C:2]"), "two-finger tap is RMB: " + r.events);

    r.events.clear();
    event(g, DrnasMousePadGesture.ACTION_DOWN, 0, 700,
        new int[] {21}, new float[] {0}, new float[] {0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_DOWN, 1, 720,
        new int[] {21, 22}, new float[] {-10, 10}, new float[] {0, 0});
    event(g, DrnasMousePadGesture.ACTION_POINTER_UP, 1, 740,
        new int[] {21, 22}, new float[] {-10, 10}, new float[] {0, 0});
    event(g, DrnasMousePadGesture.ACTION_MOVE, 0, 760,
        new int[] {21}, new float[] {30}, new float[] {0});
    event(g, DrnasMousePadGesture.ACTION_UP, 0, 780,
        new int[] {21}, new float[] {30}, new float[] {0});
    check(r.events.isEmpty(), "remaining finger movement cancels two-finger tap");

    System.out.println("PASS: mouse-pad pointer, LMB/RMB taps, stable IDs, two-finger scroll/zoom and cancel");
  }
}
