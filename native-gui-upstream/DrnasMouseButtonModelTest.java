import org.blender.blender.DrnasMouseButtonModel;
import org.blender.blender.DrnasMouseState;
import java.util.ArrayList;

public final class DrnasMouseButtonModelTest {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    ArrayList<String> events = new ArrayList<>();
    DrnasMouseButtonModel buttons = new DrnasMouseButtonModel(
        (bit, down) -> events.add(bit + (down ? "+" : "-")), 300L);

    buttons.down(4, DrnasMouseState.LEFT, 10, true);
    buttons.up(4, 50);
    check(events.toString().equals("[1+, 1-]"), "single touch is a momentary click");

    buttons.down(7, DrnasMouseState.LEFT, 180, true);
    check(buttons.isLatched(DrnasMouseState.LEFT), "double tap latches");
    buttons.up(7, 220);
    check(buttons.isHeld(DrnasMouseState.LEFT), "latch survives finger lift");
    buttons.down(8, DrnasMouseState.LEFT, 500, true);
    buttons.up(8, 520);
    check(!buttons.isHeld(DrnasMouseState.LEFT), "next tap unlocks");
    check(events.toString().equals("[1+, 1-, 1+, 1-]"), "latch emits balanced transitions");

    events.clear();
    buttons.down(21, DrnasMouseState.LEFT, 800, false);
    buttons.down(22, DrnasMouseState.LEFT, 810, false);
    buttons.up(21, 820);
    check(buttons.isHeld(DrnasMouseState.LEFT), "one pointer cannot release another owner");
    buttons.up(22, 830);
    check(!buttons.isHeld(DrnasMouseState.LEFT), "last owner releases the button");

    events.clear();
    buttons.down(1, DrnasMouseState.LEFT, 1200, false);
    buttons.down(2, DrnasMouseState.MIDDLE, 1210, false);
    buttons.down(3, DrnasMouseState.RIGHT, 1220, false);
    buttons.up(2, 1230);
    check(buttons.isHeld(DrnasMouseState.LEFT) && buttons.isHeld(DrnasMouseState.RIGHT),
        "independent mouse buttons may be held together");
    buttons.cancelPointer(3);
    check(buttons.isHeld(DrnasMouseState.LEFT) && !buttons.isHeld(DrnasMouseState.RIGHT),
        "one child cancel preserves other held buttons");
    buttons.cancel();
    check(buttons.heldMask() == 0, "global cancel releases momentary holds");

    buttons.down(9, DrnasMouseState.RIGHT, 2000, true);
    buttons.up(9, 2030);
    buttons.down(10, DrnasMouseState.RIGHT, 2200, true);
    buttons.up(10, 2250);
    buttons.cancel();
    check(buttons.isLatched(DrnasMouseState.RIGHT), "cancel preserves intentional latch");
    buttons.releaseAll();
    check(buttons.heldMask() == 0 && buttons.latchedMask() == 0,
        "lifecycle release clears held and latched state");

    events.clear();
    buttons.setMiddleAvailable(false);
    check(!buttons.down(30, DrnasMouseState.MIDDLE, 3000, false),
        "unsupported MMB is not accepted");
    check(events.isEmpty(), "unsupported button emits nothing");

    System.out.println("PASS: Pojav-style mouse buttons, ownership, double-tap latch and cancel release");
  }
}
