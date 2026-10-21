import org.blender.blender.DrnasInputHold;
import java.util.ArrayList;

public class DrnasInputHoldTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    ArrayList<Boolean> events = new ArrayList<>();
    DrnasInputHold hold = new DrnasInputHold(events::add, 300);
    hold.down(1000); check(hold.isHeld(), "down presses immediately");
    hold.up(1050, true); check(!hold.isHeld(), "single tap releases");
    hold.down(1150); hold.up(1190, true);
    check(hold.isHeld() && hold.isLatched(), "double tap latches");
    hold.down(1500); check(!hold.isHeld(), "next tap releases latch");
    hold.up(1540, true);
    hold.down(2000); hold.up(2600, true);
    hold.down(2700); hold.up(2740, true);
    check(!hold.isLatched(), "long hold cannot start a latch");
    hold.cancel(); hold.down(3000); hold.up(3040, false);
    hold.down(3100); hold.up(3140, true);
    check(!hold.isLatched(), "movement cannot start a latch");
    hold.cancel(); check(!hold.isHeld() && !hold.isLatched(), "cancel releases");
    System.out.println("PASS: tap, latch, unlock, long hold and cancel");
  }
}
