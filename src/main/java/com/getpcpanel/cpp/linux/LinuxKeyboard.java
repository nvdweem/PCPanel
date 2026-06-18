package com.getpcpanel.cpp.linux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

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

        int XSync(Pointer display, boolean discard);

        byte XKeysymToKeycode(Pointer display, NativeLong keysym);

        void XDisplayKeycodes(Pointer display, IntByReference minKeycodes, IntByReference maxKeycodes);

        // KeySym* XGetKeyboardMapping(Display*, KeyCode first, int count, int* keysyms_per_keycode_return)
        Pointer XGetKeyboardMapping(Pointer display, byte firstKeycode, int keycodeCount, IntByReference keysymsPerKeycode);

        // int XChangeKeyboardMapping(Display*, int first, int keysyms_per_keycode, KeySym* keysyms, int num_codes)
        int XChangeKeyboardMapping(Pointer display, int firstKeycode, int keysymsPerKeycode, NativeLong[] keysyms, int numCodes);

        int XFree(Pointer data);
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
    private static byte spareKeycode;
    private static int keysymsPerKeycode;
    private static boolean spareResolved;

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

    /**
     * Types arbitrary text. Each character's X11 keysym is bound onto a spare (unused) keycode via
     * {@code XChangeKeyboardMapping}, then pressed and released — the same layout-independent technique
     * {@code xdotool type} uses. This lets us emit characters that aren't on the current keyboard layout
     * at all. The spare keycode is restored to {@code NoSymbol} when done.
     */
    public static void typeText(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            var disp = display();
            if (disp == null) {
                log.warn("No X display available; cannot type text");
                return;
            }
            try {
                resolveSpare(disp);
                if (spareKeycode == 0 || keysymsPerKeycode == 0) {
                    log.error("No spare X keycode available to type text");
                    return;
                }
                input.codePoints().forEach(cp -> typeCodePoint(disp, cp));
                remapSpare(disp, 0L); // release the spare keycode back to NoSymbol
                X11.INSTANCE.XSync(disp, false);
            } catch (Throwable e) { // UnsatisfiedLinkError if libX11/libXtst is missing
                log.error("Unable to type text on Linux", e);
            }
        }
    }

    private static void typeCodePoint(Pointer disp, int cp) {
        var keysym = keysymForCodePoint(cp);
        if (keysym == 0) {
            return;
        }
        remapSpare(disp, keysym);
        X11.INSTANCE.XSync(disp, false); // let the server observe the new mapping before the key event
        var code = spareKeycode & 0xFF;
        XTest.INSTANCE.XTestFakeKeyEvent(disp, code, true, new NativeLong(0));
        XTest.INSTANCE.XTestFakeKeyEvent(disp, code, false, new NativeLong(0));
        X11.INSTANCE.XSync(disp, false);
    }

    /** Binds {@code keysym} (or {@code NoSymbol} when 0) onto every level of the spare keycode. */
    private static void remapSpare(Pointer disp, long keysym) {
        var syms = new NativeLong[keysymsPerKeycode];
        for (var i = 0; i < syms.length; i++) {
            syms[i] = new NativeLong(keysym);
        }
        X11.INSTANCE.XChangeKeyboardMapping(disp, spareKeycode & 0xFF, keysymsPerKeycode, syms, 1);
    }

    /** Maps a Unicode code point to an X11 keysym (Latin-1 direct, Unicode keysym otherwise). */
    private static long keysymForCodePoint(int cp) {
        return switch (cp) {
            case '\r' -> 0L;        // ignore; '\n' drives the newline
            case '\n' -> 0xff0dL;   // Return
            case '\t' -> 0xff09L;   // Tab
            default -> cp < 0x100 ? cp : 0x01000000L | cp;
        };
    }

    /** Finds an unused keycode once (and the layout's keysyms-per-keycode stride) to repurpose for typing. */
    private static void resolveSpare(Pointer disp) {
        if (spareResolved) {
            return;
        }
        spareResolved = true;
        var min = new IntByReference();
        var max = new IntByReference();
        X11.INSTANCE.XDisplayKeycodes(disp, min, max);
        var lo = min.getValue();
        var hi = max.getValue();
        var count = hi - lo + 1;
        if (count <= 0) {
            return;
        }
        var perRef = new IntByReference();
        var map = X11.INSTANCE.XGetKeyboardMapping(disp, (byte) lo, count, perRef);
        if (map == null) {
            return;
        }
        try {
            keysymsPerKeycode = perRef.getValue();
            if (keysymsPerKeycode <= 0) {
                return;
            }
            var syms = map.getLongArray(0, count * keysymsPerKeycode);
            for (var kc = 0; kc < count; kc++) {
                var empty = true;
                for (var j = 0; j < keysymsPerKeycode; j++) {
                    if (syms[kc * keysymsPerKeycode + j] != 0) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    spareKeycode = (byte) (lo + kc);
                    return;
                }
            }
            spareKeycode = (byte) hi; // fall back to the last keycode if none are free
        } finally {
            X11.INSTANCE.XFree(map);
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
        m.put("PAUSE", 0xff13L);                       // XK_Pause
        // Punctuation (Latin-1 keysyms)
        m.put("MINUS", 0x2dL); m.put("EQUALS", 0x3dL); m.put("OPEN_BRACKET", 0x5bL);
        m.put("CLOSE_BRACKET", 0x5dL); m.put("BACK_SLASH", 0x5cL); m.put("SEMICOLON", 0x3bL);
        m.put("QUOTE", 0x27L); m.put("COMMA", 0x2cL); m.put("PERIOD", 0x2eL); m.put("SLASH", 0x2fL);
        m.put("BACK_QUOTE", 0x60L);
        return m;
    }
}
