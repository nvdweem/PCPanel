package com.getpcpanel.cpp.linux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import lombok.extern.log4j.Log4j2;

/**
 * Synthesises keystrokes on Linux through the X11 {@code XTEST} extension
 * ({@code XTestFakeKeyEvent}) — the exact mechanism {@link java.awt.Robot} uses on X11 — so the
 * native image no longer needs the AWT windowing toolkit.
 *
 * <p>The input string is the cross-platform "{@code modifier+modifier+key}" format parsed by
 * {@link com.getpcpanel.commands.KeyMacro}; tokens (AWT {@code VK_}-suffix names) are mapped to X11
 * keysyms, resolved to keycodes via the current keyboard mapping, then pressed/released.
 *
 * <p>Works against an X server (native X11 or XWayland). On a pure Wayland session with no X server
 * {@code XOpenDisplay} returns null and keystrokes are skipped with a warning.
 */
@Log4j2
public final class LinuxKeyboard {
    private interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String name);

        int XFlush(Pointer display);

        byte XKeysymToKeycode(Pointer display, NativeLong keysym);
    }

    private interface XTest extends Library {
        XTest INSTANCE = Native.load("Xtst", XTest.class);

        int XTestFakeKeyEvent(Pointer display, int keycode, boolean isPress, NativeLong delay);
    }

    /** AWT-VK-style token (the part after {@code VK_}) → X11 keysym. */
    private static final Map<String, Long> KEYSYMS = buildKeysyms();
    private static final Object LOCK = new Object();
    private static Pointer display;
    private static boolean displayResolved;

    private LinuxKeyboard() {
    }

    public static void executeKeyStroke(String input) {
        if (input == null || input.contains("UNDEFINED")) {
            return;
        }
        var parts = input.replace(" ", "").split("\\+");
        synchronized (LOCK) {
            var disp = display();
            if (disp == null) {
                log.warn("No X display available; cannot synthesise keystroke '{}'", input);
                return;
            }
            var pressed = new ArrayList<Byte>();
            try {
                for (var i = 0; i < parts.length - 1; i++) {
                    var sym = modifierKeysym(parts[i]);
                    if (sym == 0) {
                        log.error("bad keystroke modifier: {}", parts[i]);
                    } else {
                        pressed.add(sendKeysym(disp, sym, true));
                    }
                }
                var keySym = keysym(parts[parts.length - 1]);
                if (keySym == 0) {
                    log.error("Unsupported Linux keystroke key '{}' in '{}'", parts[parts.length - 1], input);
                } else {
                    pressed.add(sendKeysym(disp, keySym, true));
                }
            } catch (Throwable e) { // UnsatisfiedLinkError if libXtst is missing
                log.error("Unable to post Linux keystroke '{}'", input, e);
            } finally {
                for (var i = pressed.size() - 1; i >= 0; i--) {
                    var code = pressed.get(i);
                    if (code != 0) {
                        try {
                            XTest.INSTANCE.XTestFakeKeyEvent(disp, code & 0xFF, false, new NativeLong(0));
                        } catch (Throwable e) {
                            log.error("Unable to release keycode {}", code, e);
                        }
                    }
                }
                X11.INSTANCE.XFlush(disp);
            }
        }
    }

    /** Presses the given keysym and returns the keycode used (0 if it has no keycode on this layout). */
    private static byte sendKeysym(Pointer disp, long keysym, boolean press) {
        var keycode = X11.INSTANCE.XKeysymToKeycode(disp, new NativeLong(keysym));
        if (keycode != 0) {
            XTest.INSTANCE.XTestFakeKeyEvent(disp, keycode & 0xFF, press, new NativeLong(0));
        } else {
            log.error("No keycode for keysym 0x{}", Long.toHexString(keysym));
        }
        return keycode;
    }

    private static Pointer display() {
        if (!displayResolved) {
            displayResolved = true;
            try {
                display = X11.INSTANCE.XOpenDisplay(null);
            } catch (Throwable e) {
                log.error("Unable to open X display for keystroke injection", e);
            }
        }
        return display;
    }

    static long modifierKeysym(String mod) {
        return switch (mod) {
            case "ctrl" -> 0xffe3L;                      // Control_L
            case "shift" -> 0xffe1L;                      // Shift_L
            case "alt" -> 0xffe9L;                        // Alt_L
            case "cmd", "command", "windows", "meta" -> 0xffebL; // Super_L
            default -> 0;
        };
    }

    static long keysym(String token) {
        var t = token.toUpperCase();
        if (t.length() == 1) {
            var c = t.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return Character.toLowerCase(c); // keysym for letters is the lowercase Latin-1 codepoint
            }
            if (c >= '0' && c <= '9') {
                return c;
            }
        }
        return KEYSYMS.getOrDefault(t, 0L);
    }

    @SuppressWarnings("java:S138") // long but flat lookup table
    private static Map<String, Long> buildKeysyms() {
        Map<String, Long> m = new HashMap<>();
        // Function keys (XK_F1..XK_F24 are contiguous from 0xffbe)
        for (var i = 1; i <= 24; i++) {
            m.put("F" + i, 0xffbeL + (i - 1));
        }
        // Control / navigation
        m.put("ENTER", 0xff0dL); m.put("TAB", 0xff09L); m.put("SPACE", 0x20L);
        m.put("BACK_SPACE", 0xff08L); m.put("ESCAPE", 0xff1bL); m.put("ESC", 0xff1bL);
        m.put("DELETE", 0xffffL); m.put("INSERT", 0xff63L); m.put("HOME", 0xff50L); m.put("END", 0xff57L);
        m.put("PAGE_UP", 0xff55L); m.put("PAGE_DOWN", 0xff56L);
        m.put("LEFT", 0xff51L); m.put("UP", 0xff52L); m.put("RIGHT", 0xff53L); m.put("DOWN", 0xff54L);
        // Punctuation (Latin-1 keysyms)
        m.put("MINUS", 0x2dL); m.put("EQUALS", 0x3dL); m.put("OPEN_BRACKET", 0x5bL);
        m.put("CLOSE_BRACKET", 0x5dL); m.put("BACK_SLASH", 0x5cL); m.put("SEMICOLON", 0x3bL);
        m.put("QUOTE", 0x27L); m.put("COMMA", 0x2cL); m.put("PERIOD", 0x2eL); m.put("SLASH", 0x2fL);
        m.put("BACK_QUOTE", 0x60L);
        return m;
    }
}
