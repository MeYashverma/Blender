import org.blender.blender.DrnasMouseState;
import java.util.ArrayList;

public class DrnasMouseStateTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  /** Records "L+", "R-", ... so duplicate or missing native events are visible in the test. */
  private static final class Recorder implements DrnasMouseState.Sink {
    final ArrayList<String> events = new ArrayList<>();
    public void mouseButton(int bit, boolean down) {
      String name = bit == DrnasMouseState.LEFT ? "L"
          : bit == DrnasMouseState.RIGHT ? "R" : "M";
      events.add(name + (down ? "+" : "-"));
    }
    int count() { return events.size(); }
    String at(int index) { return events.get(index); }
  }

  public static void main(String[] args) {
    // 1. A press and a release produce exactly one native event each, and repeats are ignored.
    Recorder recorder = new Recorder();
    DrnasMouseState mouse = new DrnasMouseState(recorder);
    check(mouse.press(DrnasMouseState.LEFT), "first press is a real change");
    check(!mouse.press(DrnasMouseState.LEFT), "pressing a held button again does nothing");
    check(recorder.count() == 1 && recorder.at(0).equals("L+"), "exactly one left-down");
    check(mouse.release(DrnasMouseState.LEFT), "release is a change");
    check(!mouse.release(DrnasMouseState.LEFT), "releasing a free button does nothing");
    check(recorder.count() == 2 && recorder.at(1).equals("L-"), "exactly one left-up");

    // 2. Simultaneous, independent buttons: middle drag must survive a left click.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    mouse.press(DrnasMouseState.MIDDLE);
    mouse.press(DrnasMouseState.LEFT);
    check(mouse.heldMask() == (DrnasMouseState.MIDDLE | DrnasMouseState.LEFT),
        "both buttons are held at once");
    mouse.release(DrnasMouseState.LEFT);
    check(mouse.isHeld(DrnasMouseState.MIDDLE), "releasing left must not release middle");
    mouse.press(DrnasMouseState.RIGHT);
    check(mouse.isHeld(DrnasMouseState.MIDDLE) && mouse.isHeld(DrnasMouseState.RIGHT),
        "right and middle coexist");
    mouse.releaseAll();
    check(mouse.heldMask() == 0, "releaseAll clears everything");
    check(recorder.count() == 6, "one event per real transition: " + recorder.events);

    // 3. A momentary click is down then up, and unlocks nothing it did not lock.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    mouse.click(DrnasMouseState.LEFT);
    check(recorder.events.toString().equals("[L+, L-]"), "click is press+release: " + recorder.events);

    // 4. Latching: double tap holds the button, the next tap unlocks it.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    check(mouse.toggleLatch(DrnasMouseState.RIGHT), "latch presses the button");
    check(mouse.isLatched(DrnasMouseState.RIGHT) && mouse.isHeld(DrnasMouseState.RIGHT),
        "latched button is really held down");
    check(recorder.events.toString().equals("[R+]"), "latch must not send a synthetic release");
    check(mouse.toggleLatch(DrnasMouseState.RIGHT), "second tap unlocks");
    check(!mouse.isLatched(DrnasMouseState.RIGHT) && !mouse.isHeld(DrnasMouseState.RIGHT),
        "latch cleared");
    check(recorder.events.toString().equals("[R+, R-]"), "unlock sends exactly one release");

    // 5. Tapping a latched button must not produce a second down.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    mouse.latch(DrnasMouseState.LEFT);
    mouse.click(DrnasMouseState.LEFT);
    check(recorder.events.toString().equals("[L+, L-]"), "no duplicate press: " + recorder.events);
    check(!mouse.isLatched(DrnasMouseState.LEFT), "click on latched button unlocks it");

    // 6. releaseAll also drops latches, so nothing survives a pause or a cancelled gesture.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    mouse.latch(DrnasMouseState.LEFT);
    mouse.press(DrnasMouseState.RIGHT);
    mouse.releaseAll();
    check(mouse.latchedMask() == 0 && mouse.heldMask() == 0, "lifecycle release clears all state");
    check(recorder.events.toString().equals("[L+, R+, L-, R-]"), "each held button released once: "
        + recorder.events);

    // 7. Middle button is gated on native support instead of silently doing nothing.
    recorder = new Recorder();
    mouse = new DrnasMouseState(recorder);
    mouse.setMiddleAvailable(false);
    check(!mouse.press(DrnasMouseState.MIDDLE), "press refused when unsupported");
    check(!mouse.latch(DrnasMouseState.MIDDLE), "latch refused when unsupported");
    check(recorder.count() == 0, "no event may be emitted for an unsupported button");
    check(!mouse.isHeld(DrnasMouseState.MIDDLE), "nothing is marked held");

    System.out.println("PASS: press/release, independence, click, latch/unlock, no duplicates, "
        + "lifecycle release and middle-button gating");
  }
}
