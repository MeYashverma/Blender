import org.blender.blender.DrnasShortcutStrip;

/** Android-free tests for the shortcut strip order/visibility model. */
public final class DrnasShortcutStripTest {

  private static int failures;

  private static void check(boolean condition, String message) {
    if (!condition) {
      failures++;
      System.out.println("FAIL: " + message);
    }
  }

  private static boolean sameOrder(DrnasShortcutStrip strip, String... expected) {
    if (strip.size() != expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if (!expected[i].equals(strip.labelAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static void main(String[] args) {
    final String[] shipped = {"Move", "Rotate", "Scale", "Axis X", "Undo", "Save", "Search"};

    // 1. A fresh strip keeps the shipped order with everything visible.
    DrnasShortcutStrip strip = new DrnasShortcutStrip(shipped);
    check(sameOrder(strip, "Move", "Rotate", "Scale", "Axis X", "Undo", "Save", "Search"),
        "default order is the shipped order");
    check(strip.visibleCount() == 7, "every button starts visible");

    // 2. Reordering keeps the button set intact and clamps at both ends.
    check(strip.move(0, 2), "moving down reports a change");
    check(sameOrder(strip, "Rotate", "Scale", "Move", "Axis X", "Undo", "Save", "Search"),
        "move down by two");
    check(!strip.move(0, -5), "moving past the top is a no-op");
    check(strip.move(strip.size() - 1, 99), "moving past the bottom clamps");
    check("Search".equals(strip.labelAt(strip.size() - 1)), "last item stays last");

    // 3. Hide/unhide and the "never hide everything" guard.
    check(!strip.isHidden(0), "nothing hidden by default");
    check(strip.toggleHidden(0), "toggle hides");
    check(strip.isHidden(0), "button reports hidden");
    check(strip.visibleCount() == 6, "hidden buttons are not counted");
    check(!strip.toggleHidden(0), "toggle unhides");
    check(strip.visibleCount() == 7, "visible count recovers");

    // 4. Persistence round trip keeps order and hidden state.
    strip.move(0, 1);
    strip.setHidden(2, true);
    final String saved = strip.encode();
    DrnasShortcutStrip reloaded = new DrnasShortcutStrip(shipped);
    reloaded.applyPref(saved);
    check(sameOrder(reloaded, strip.labelAt(0), strip.labelAt(1), strip.labelAt(2),
        strip.labelAt(3), strip.labelAt(4), strip.labelAt(5), strip.labelAt(6)),
        "order survives a round trip");
    check(reloaded.isHiddenLabel(strip.labelAt(2)), "hidden state survives a round trip");
    check(saved.equals(reloaded.encode()), "encoding is stable");

    // 5. Stale preferences: unknown labels are dropped, missing ones appended, duplicates ignored.
    DrnasShortcutStrip stale = new DrnasShortcutStrip(shipped);
    stale.applyPref("Save,UnknownButton,Save,Search|UnknownButton");
    check(sameOrder(stale, "Save", "Search", "Move", "Rotate", "Scale", "Axis X", "Undo"),
        "unknown labels drop, missing labels append, duplicates collapse");
    check(stale.visibleCount() == 7, "an unknown hidden label hides nothing");

    // 6. Corruption tolerance: never throws, always ends with a usable strip.
    DrnasShortcutStrip corrupt = new DrnasShortcutStrip(shipped);
    corrupt.applyPref("|||,,,%2C%25%%||");
    check(corrupt.size() == 7, "garbage keeps the full button set");
    corrupt.applyPref(null);
    check(corrupt.size() == 7 && corrupt.visibleCount() == 7, "null resets to defaults");

    // 7. Hiding every button is refused so the strip cannot become invisible.
    DrnasShortcutStrip allHidden = new DrnasShortcutStrip(shipped);
    for (int i = 0; i < allHidden.size(); i++) {
      allHidden.setHidden(i, true);
    }
    allHidden.applyPref(allHidden.encode());
    check(allHidden.visibleCount() >= 1, "at least one button always stays visible");

    // 8. A preference written by a build with fewer buttons must not resurrect them.
    DrnasShortcutStrip smaller = new DrnasShortcutStrip(shipped);
    smaller.applyPref("Save");
    check(smaller.size() == 7, "the current build's full set is restored");
    check("Save".equals(smaller.labelAt(0)), "the saved order is honoured");
    check(smaller.isHiddenLabel("Save") == false, "nothing is hidden by an order-only preference");

    // 9. Empty/short default lists do not break construction.
    DrnasShortcutStrip empty = new DrnasShortcutStrip(new String[0]);
    check(empty.size() == 0 && empty.visibleCount() == 0, "an empty strip is valid");
    empty.applyPref("a,b,c");
    check(empty.size() == 0, "an empty strip stays empty");
    DrnasShortcutStrip nulls = new DrnasShortcutStrip(null);
    check(nulls.size() == 0, "a null default list is tolerated");

    // 10. Escaping: labels containing separators survive a round trip.
    DrnasShortcutStrip escaped = new DrnasShortcutStrip(new String[] {"A,B", "C|D", "50%"});
    check(escaped.encode().equals("A%2CB,C%7CD,50%25|"), "separators are escaped");
    DrnasShortcutStrip back = new DrnasShortcutStrip(new String[] {"A,B", "C|D", "50%"});
    back.applyPref(escaped.encode());
    check("A,B".equals(back.labelAt(0)) && "C|D".equals(back.labelAt(1))
        && "50%".equals(back.labelAt(2)), "escaped labels round trip");

    if (failures > 0) {
      throw new AssertionError(failures + " shortcut strip checks failed");
    }
    System.out.println("PASS: shortcut strip default order, reorder clamping, hide/unhide, "
        + "persistence round trip, stale and corrupt preferences, escaping");
  }
}
