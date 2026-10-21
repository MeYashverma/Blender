#!/usr/bin/env python3
"""Extend the DRNAS native input bridge for the touch control layer.

Run **after** ``patch_native_ghost.py``; it rewrites the dispatch block that script inserts.
Fail closed: every anchor must match exactly once, so an upstream change (or a re-ordered patch
chain) stops the build instead of silently producing a half-patched input bridge.

What it adds, all of it reachable only through the existing ``nativeOnDrnasInput`` JNI:

* action 8 / 9  – middle mouse button down / up (viewport orbit, file-browser pan)
* action 10 / 11 – Shift hold on / off (three-finger pan = Shift + middle)
* action 12      – horizontal wheel
* Android keycodes that Blender's keymap needs but the stock conversion drops:
  F1-F12, numpad 0-9 and its operators, Home/End/PageUp/PageDown, period, comma, minus,
  equals and accent-grave. Without these the existing "Search", "Front", "Side" and "Top"
  buttons in the shortcut strip emit nothing at all.
* ``nativeDrnasCapabilities()`` so the Java overlay can hide what an older library cannot do
  instead of pretending. Unpatched libraries simply do not export the symbol and Java catches
  the resulting UnsatisfiedLinkError.

GPL-2.0-or-later, like the upstream sources this patches.
"""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
header = root / "intern/ghost/intern/GHOST_SystemAndroid.hh"
system = root / "intern/ghost/intern/GHOST_SystemAndroid.cc"
main = root / "intern/ghost/intern/GHOST_AndroidMain.cc"

CAPABILITY_MIDDLE = 1
CAPABILITY_SHIFT = 2
CAPABILITY_HSCROLL = 4
CAPABILITY_KEYS = 8
CAPABILITY_GESTURES = 16


def once(src: str, old: str, new: str, what: str) -> str:
    count = src.count(old)
    if count != 1:
        raise RuntimeError(f"{what}: expected one occurrence, found {count}: {old[:120]!r}")
    return src.replace(old, new, 1)


# ---------------------------------------------------------------- header: new state
h = header.read_text()
h = once(
    h,
    "  float drnas_delta_remainder_zoom_ = 0.0f;",
    """  float drnas_delta_remainder_zoom_ = 0.0f;
  /* Touch-extra state: horizontal wheel accumulation and the Shift the pan gesture holds. */
  float drnas_hwheel_remainder_ = 0.0f;
  bool drnas_shift_ = false;""",
    "header state",
)
header.write_text(h)

# ---------------------------------------------------------------- input queue accepts 1..12
c = system.read_text()
c = once(
    c,
    """  if (action < 1 || action > 7) {
    return;
  }""",
    """  if (action < 1 || action > 12) {
    return;
  }""",
    "queue range",
)

# ---------------------------------------------------------------- dispatch: extra actions
c = once(
    c,
    """  const bool left = event.action == 2 || event.action == 3;
  const bool down = event.action == 2 || event.action == 4;
  const uint32_t bit = left ? 1u : 2u;
  if (down == ((drnas_buttons_ & bit) != 0)) {
    return;
  }""",
    """  if (event.action == 12) {
    /* Horizontal wheel, accumulated and clamped exactly like the vertical one. */
    drnas_hwheel_remainder_ += std::clamp(event.value, -8.0f, 8.0f);
    int count = int(std::trunc(drnas_hwheel_remainder_));
    count = std::clamp(count, -5, 5);
    drnas_hwheel_remainder_ -= float(count);
    for (int i = 0; i < abs(count); i++) {
      pushEvent(std::make_unique<GHOST_EventWheel>(
          getMilliSeconds(), window_, GHOST_kEventWheelAxisHorizontal, count > 0 ? 1 : -1));
    }
    return;
  }
  if (event.action == 10 || event.action == 11) {
    /* Shift hold for the touch pan gesture. The window manager re-reads getModifierKeys() on
     * every button event and injects a press/release of its own when the two disagree, so
     * meta_state_ has to tell the same story as the synthetic key event. */
    const bool down = event.action == 10;
    if (drnas_shift_ == down) {
      return;
    }
    drnas_shift_ = down;
    if (down) {
      meta_state_ |= AMETA_SHIFT_ON;
    }
    else {
      meta_state_ &= ~AMETA_SHIFT_ON;
    }
    pushEvent(std::make_unique<GHOST_EventKey>(getMilliSeconds(),
                                               down ? GHOST_kEventKeyDown : GHOST_kEventKeyUp,
                                               window_,
                                               GHOST_kKeyLeftShift,
                                               false));
    return;
  }

  GHOST_TButton mask;
  uint32_t bit;
  bool down;
  switch (event.action) {
    case 2:
      mask = GHOST_kButtonMaskLeft;
      bit = 1u;
      down = true;
      break;
    case 3:
      mask = GHOST_kButtonMaskLeft;
      bit = 1u;
      down = false;
      break;
    case 4:
      mask = GHOST_kButtonMaskRight;
      bit = 2u;
      down = true;
      break;
    case 5:
      mask = GHOST_kButtonMaskRight;
      bit = 2u;
      down = false;
      break;
    case 8:
      mask = GHOST_kButtonMaskMiddle;
      bit = 4u;
      down = true;
      break;
    case 9:
      mask = GHOST_kButtonMaskMiddle;
      bit = 4u;
      down = false;
      break;
    default:
      return;
  }
  if (down == ((drnas_buttons_ & bit) != 0)) {
    return;
  }""",
    "dispatch buttons",
)

c = once(
    c,
    """  const GHOST_TButton mask = left ? GHOST_kButtonMaskLeft : GHOST_kButtonMaskRight;
  buttons_.set(mask, down);""",
    """  buttons_.set(mask, down);""",
    "dispatch mask use",
)

# ---------------------------------------------------------------- extended keycodes
c = once(
    c,
    """  if (keycode >= AKEYCODE_0 && keycode <= AKEYCODE_9) {
    return GHOST_TKey(GHOST_kKey0 + (keycode - AKEYCODE_0));
  }""",
    """  if (keycode >= AKEYCODE_0 && keycode <= AKEYCODE_9) {
    return GHOST_TKey(GHOST_kKey0 + (keycode - AKEYCODE_0));
  }
  /* DRNAS: Blender's default keymap uses function keys, the numpad and Home/End for view
   * control. The stock conversion dropped all of them, which made the on-screen "Search",
   * "Front", "Side", "Top" and "Frame" buttons dead keys on Android. */
  if (keycode >= AKEYCODE_F1 && keycode <= AKEYCODE_F12) {
    return GHOST_TKey(GHOST_kKeyF1 + (keycode - AKEYCODE_F1));
  }
  if (keycode >= AKEYCODE_NUMPAD_0 && keycode <= AKEYCODE_NUMPAD_9) {
    return GHOST_TKey(GHOST_kKeyNumpad0 + (keycode - AKEYCODE_NUMPAD_0));
  }""",
    "extended key ranges",
)

# Extended key cases. Upstream/simfeo already maps a few of these (MOVE_HOME for example), and a
# second case with the same value is a hard C++ error, so each key is only added when the file does
# not mention it yet. This keeps the patch correct across upstream drift in either direction.
EXTENDED_KEYS = [
    ("AKEYCODE_NUMPAD_DOT", "GHOST_kKeyNumpadPeriod"),
    ("AKEYCODE_NUMPAD_ENTER", "GHOST_kKeyNumpadEnter"),
    ("AKEYCODE_NUMPAD_ADD", "GHOST_kKeyNumpadPlus"),
    ("AKEYCODE_NUMPAD_SUBTRACT", "GHOST_kKeyNumpadMinus"),
    ("AKEYCODE_NUMPAD_MULTIPLY", "GHOST_kKeyNumpadAsterisk"),
    ("AKEYCODE_NUMPAD_DIVIDE", "GHOST_kKeyNumpadSlash"),
    ("AKEYCODE_MOVE_HOME", "GHOST_kKeyHome"),
    ("AKEYCODE_MOVE_END", "GHOST_kKeyEnd"),
    ("AKEYCODE_PAGE_UP", "GHOST_kKeyUpPage"),
    ("AKEYCODE_PAGE_DOWN", "GHOST_kKeyDownPage"),
    ("AKEYCODE_PERIOD", "GHOST_kKeyPeriod"),
    ("AKEYCODE_COMMA", "GHOST_kKeyComma"),
    ("AKEYCODE_MINUS", "GHOST_kKeyMinus"),
    ("AKEYCODE_EQUALS", "GHOST_kKeyEqual"),
    ("AKEYCODE_GRAVE", "GHOST_kKeyAccentGrave"),
    ("AKEYCODE_DPAD_CENTER", "GHOST_kKeyNumpadEnter"),
]

def _missing_keys(src: str) -> list[tuple[str, str]]:
    present = set(re.findall(r"case\s+(AKEYCODE_[A-Z0-9_]+)\s*:", src))
    return [(code, target) for code, target in EXTENDED_KEYS if code not in present]

KEY_ANCHOR_OLD = """    case AKEYCODE_DPAD_DOWN:
      return GHOST_kKeyDownArrow;
    default:
      return GHOST_kKeyUnknown;"""

missing = _missing_keys(c)
if not missing:
    raise SystemExit("no extended key codes were added; the upstream conversion changed")
insert = "".join(
    "    case %s:\n      return %s;\n" % (code, target) for code, target in missing)
c = once(
    c,
    KEY_ANCHOR_OLD,
    KEY_ANCHOR_OLD.replace(
        "    default:\n      return GHOST_kKeyUnknown;",
        "    /* DRNAS: numpad operators, navigation and punctuation used by Blender's keymap. */\n"
        + insert
        + "    default:\n      return GHOST_kKeyUnknown;"),
    "extended key cases",
)

# A duplicate case value is a compile error, and it is cheap to prove here rather than in CI.
_seen = re.findall(r"case\s+(AKEYCODE_[A-Z0-9_]+)\s*:", c)
_dupes = sorted({code for code in _seen if _seen.count(code) > 1})
if _dupes:
    raise SystemExit(f"duplicate key cases would not compile: {_dupes}")
for code, _target in missing:
    if c.count(f"case {code}:") != 1:
        raise SystemExit(f"{code} was not added exactly once")
system.write_text(c)

# ---------------------------------------------------------------- capability probe
m = main.read_text()
anchor = "/* JNI is called from Android's UI thread: never push GHOST events here. */"
m = once(
    m,
    anchor,
    f"""/* Bitmask so the Java overlay can hide what this library cannot do. An older
 * libblender.so simply does not export this symbol and the overlay catches the resulting
 * UnsatisfiedLinkError, which is how the Java-only repack path stays honest. */
#define DRNAS_CAP_MIDDLE {CAPABILITY_MIDDLE}
#define DRNAS_CAP_SHIFT {CAPABILITY_SHIFT}
#define DRNAS_CAP_HSCROLL {CAPABILITY_HSCROLL}
#define DRNAS_CAP_KEYS {CAPABILITY_KEYS}
#define DRNAS_CAP_GESTURES {CAPABILITY_GESTURES}

extern "C" JNIEXPORT jint JNICALL
Java_org_blender_blender_BlenderActivity_nativeDrnasCapabilities(JNIEnv * /*env*/,
                                                                jobject /*thiz*/)
{{
  return jint(DRNAS_CAP_MIDDLE | DRNAS_CAP_SHIFT | DRNAS_CAP_HSCROLL | DRNAS_CAP_KEYS |
              DRNAS_CAP_GESTURES);
}}

""" + anchor,
    "capability JNI",
)
main.write_text(m)

# ---------------------------------------------------------------- self checks
checks = [
    (c, "GHOST_kButtonMaskMiddle", "middle button dispatch"),
    (c, "AKEYCODE_F1 && keycode <= AKEYCODE_F12", "function keys"),
    (c, "GHOST_kKeyNumpadPeriod", "numpad period"),
    (c, "event.action == 12", "horizontal wheel"),
    (m, "nativeDrnasCapabilities", "capability JNI"),
    (h, "drnas_shift_", "shift state"),
]
for source, needle, what in checks:
    assert needle in source, f"missing {what}"
assert c.count("const GHOST_TButton mask =") == 0, "old mask selection must be gone"
assert "drnas_buttons_ & bit" in c, "button edge detection kept"
print("PASS: middle button, shift hold, horizontal wheel, extended keys and capability probe")
