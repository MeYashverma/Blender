package org.blender.blender;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Order and visibility of the shortcut strip, decoupled from the Android views.
 *
 * <p>The strip is built from a fixed set of buttons, but the useful order depends on the user: a
 * modeller wants G/R/S and axis keys first, a texture painter wants undo/save. This model keeps one
 * canonical label list, a user order that is a permutation of it, and a hidden set. It is persisted
 * as a single preference string, is tolerant of older/newer builds (unknown labels are dropped,
 * newly shipped labels are appended), and never throws on corrupt input.
 *
 * <p>Pure Java so it is unit tested without a device.
 */
public final class DrnasShortcutStrip {

  public static final String PREF_KEY = "shortcut_strip";

  private final ArrayList<String> canonical = new ArrayList<>();
  private final ArrayList<String> order = new ArrayList<>();
  private final HashSet<String> hidden = new HashSet<>();

  public DrnasShortcutStrip(String[] defaultLabels) {
    if (defaultLabels != null) {
      for (String label : defaultLabels) {
        if (label != null && label.length() > 0 && !canonical.contains(label)) {
          canonical.add(label);
        }
      }
    }
    reset();
  }

  /** Restores the shipped order with every button visible. */
  public void reset() {
    order.clear();
    order.addAll(canonical);
    hidden.clear();
  }

  public int size() {
    return order.size();
  }

  public int visibleCount() {
    return order.size() - hidden.size();
  }

  public String labelAt(int slot) {
    if (slot < 0 || slot >= order.size()) {
      return null;
    }
    return order.get(slot);
  }

  public int indexOf(String label) {
    return label == null ? -1 : order.indexOf(label);
  }

  public boolean isHidden(int slot) {
    final String label = labelAt(slot);
    return label != null && hidden.contains(label);
  }

  public boolean isHiddenLabel(String label) {
    return label != null && hidden.contains(label);
  }

  public void setHidden(int slot, boolean value) {
    final String label = labelAt(slot);
    if (label == null) {
      return;
    }
    if (value) {
      hidden.add(label);
    } else {
      hidden.remove(label);
    }
  }

  public boolean toggleHidden(int slot) {
    final String label = labelAt(slot);
    if (label == null) {
      return false;
    }
    final boolean next = !hidden.contains(label);
    setHidden(slot, next);
    return next;
  }

  /**
   * Moves the entry at {@code slot} by {@code delta} places, clamped to the list. Returns true when
   * the order actually changed, so a caller can skip persisting a no-op.
   */
  public boolean move(int slot, int delta) {
    if (slot < 0 || slot >= order.size() || delta == 0) {
      return false;
    }
    int target = Math.max(0, Math.min(order.size() - 1, slot + delta));
    if (target == slot) {
      return false;
    }
    final String label = order.remove(slot);
    order.add(target, label);
    return true;
  }

  /** Moves a label to an explicit slot, used by drag-based editors. */
  public boolean moveTo(String label, int target) {
    final int from = indexOf(label);
    if (from < 0) {
      return false;
    }
    final int to = Math.max(0, Math.min(order.size() - 1, target));
    if (from == to) {
      return false;
    }
    order.add(to, order.remove(from));
    return true;
  }

  public String encode() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < order.size(); i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(escape(order.get(i)));
    }
    builder.append('|');
    boolean first = true;
    for (String label : order) {
      if (!hidden.contains(label)) {
        continue;
      }
      if (!first) {
        builder.append(',');
      }
      builder.append(escape(label));
      first = false;
    }
    return builder.toString();
  }

  /** Applies a stored preference. Anything unrecognised is ignored; nothing is thrown. */
  public void applyPref(String encoded) {
    reset();
    if (encoded == null || encoded.length() == 0) {
      return;
    }
    final int bar = encoded.indexOf('|');
    final String orderPart = bar < 0 ? encoded : encoded.substring(0, bar);
    final String hiddenPart = bar < 0 ? "" : encoded.substring(bar + 1);

    final ArrayList<String> parsed = new ArrayList<>();
    for (String token : orderPart.split(",")) {
      final String label = unescape(token.trim());
      // Skip blanks, unknown labels (another version's button) and duplicates, so a corrupt or
      // stale preference can only ever lose the customisation, never a control.
      if (label.length() == 0 || !canonical.contains(label) || parsed.contains(label)) {
        continue;
      }
      parsed.add(label);
    }
    if (!parsed.isEmpty()) {
      // Labels that ship later than the saved preference are appended instead of vanishing.
      for (String label : canonical) {
        if (!parsed.contains(label)) {
          parsed.add(label);
        }
      }
      order.clear();
      order.addAll(parsed);
    }
    for (String token : hiddenPart.split(",")) {
      final String label = unescape(token.trim());
      if (canonical.contains(label)) {
        hidden.add(label);
      }
    }
    // Never hide everything: at least one button stays reachable.
    if (order.size() > 0 && hidden.size() >= order.size()) {
      hidden.remove(order.get(0));
    }
  }

  public static String staticEncode(String[] order, boolean[] hidden) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < order.length; i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(escape(order[i]));
    }
    builder.append('|');
    boolean first = true;
    for (int i = 0; i < order.length; i++) {
      if (!hidden[i]) {
        continue;
      }
      if (!first) {
        builder.append(',');
      }
      builder.append(escape(order[i]));
      first = false;
    }
    return builder.toString();
  }

  /**
   * Labels are plain ASCII in every shipped language, but a translator may add a comma, so the
   * separator is escaped instead of being assumed absent.
   */
  private static String escape(String label) {
    return label.replace("%", "%25").replace(",", "%2C").replace("|", "%7C");
  }

  private static String unescape(String label) {
    return label.replace("%7C", "|").replace("%2C", ",").replace("%25", "%");
  }
}
