package com.getpcpanel.integration.keyboard.platform.osx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.getpcpanel.integration.keyboard.Keyboard;
import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.os.OsxPermissionHelper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * macOS {@link Keyboard} backend: synthesises keystrokes through CoreGraphics {@code CGEvent}s,
 * replacing {@link java.awt.Robot} (AWT is unavailable in the macOS GraalVM native image). Media keys
 * delegate to {@link OsxMediaControl} (AppleScript), since macOS has no media-key API safely reachable
 * from Java.
 *
 * <p>Architecture independent: every native handle is passed as an opaque {@link Pointer} and every
 * scalar uses a fixed C width (CGKeyCode = uint16, CGEventTapLocation = uint32, CGEventFlags = uint64),
 * all identical on Intel (x86_64) and Apple Silicon (arm64) since both are LP64. The virtual key codes
 * are hardware-independent ANSI keyboard positions, so the same table works on every Mac.
 *
 * <p>Requires the Accessibility permission (System Settings &gt; Privacy &amp; Security &gt; Accessibility);
 * without it macOS silently drops the synthesised events. See
 * {@link com.getpcpanel.util.os.OsxPermissionHelper#isAccessibilityGranted()}.
 */
@Log4j2
@ApplicationScoped
@Unremovable
@MacBuild
class OsxKeyboard implements Keyboard {
    private final AtomicBoolean accessibilityWarned = new AtomicBoolean();

    @Inject
    OsxMediaControl mediaControl;

    private interface CoreGraphics extends Library {
        CoreGraphics INSTANCE = Native.load("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", CoreGraphics.class);

        // CGEventRef CGEventCreateKeyboardEvent(CGEventSourceRef source, CGKeyCode virtualKey, bool keyDown)
        Pointer CGEventCreateKeyboardEvent(Pointer source, short virtualKey, boolean keyDown);

        // void CGEventSetFlags(CGEventRef event, CGEventFlags flags)
        void CGEventSetFlags(Pointer event, long flags);

        // void CGEventKeyboardSetUnicodeString(CGEventRef event, UniCharCount length, const UniChar *string)
        // UniCharCount is unsigned long (8 bytes on LP64); UniChar is a UTF-16 code unit (unsigned short).
        void CGEventKeyboardSetUnicodeString(Pointer event, NativeLong stringLength, short[] unicodeString);

        // void CGEventPost(CGEventTapLocation tap, CGEventRef event)
        void CGEventPost(int tap, Pointer event);
    }

    private interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation.class);

        void CFRelease(Pointer cf);
    }

    private static final int K_CG_HID_EVENT_TAP = 0;

    // CGEventFlags (uint64) modifier masks.
    private static final long FLAG_SHIFT = 0x00020000L;
    private static final long FLAG_CONTROL = 0x00040000L;
    private static final long FLAG_ALTERNATE = 0x00080000L; // option
    private static final long FLAG_COMMAND = 0x00100000L;

    private static final short UNKNOWN = -1;

    /** AWT-VK-style token (the part after {@code VK_}) → macOS virtual key code (kVK_ANSI_*). */
    private static final Map<String, Short> KEY_CODES = buildKeyCodes();

    /**
     * Executes a "{@code modifier+modifier+key}" combination by posting a key-down then key-up CGEvent
     * with the modifiers folded into the event flags. Unknown keys are logged and skipped rather than
     * throwing.
     */
    @Override
    public void executeKeyStroke(String input) {
        if (input == null || input.contains("UNDEFINED")) {
            return;
        }
        warnIfAccessibilityNotGranted();
        var parts = input.replace(" ", "").split("\\+");
        long flags = 0;
        for (var i = 0; i < parts.length - 1; i++) {
            var flag = modifierFlag(parts[i]);
            if (flag == 0) {
                log.error("bad keystroke modifier: {}", parts[i]);
            } else {
                flags |= flag;
            }
        }
        var keyCode = keyCode(parts[parts.length - 1]);
        if (keyCode == UNKNOWN) {
            log.error("Unsupported macOS keystroke key '{}' in '{}'", parts[parts.length - 1], input);
            return;
        }
        try {
            postKey(keyCode, flags);
        } catch (Throwable e) { // UnsatisfiedLinkError if the frameworks are somehow missing
            log.error("Unable to post macOS keystroke '{}'", input, e);
        }
    }

    /**
     * Types arbitrary text by posting each UTF-16 code unit as a CGEvent carrying a Unicode string,
     * which is layout-independent. Newlines and tabs are sent as real Return/Tab key codes since the
     * Unicode-string path does not synthesise them.
     */
    @Override
    public void typeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        warnIfAccessibilityNotGranted();
        try {
            for (var i = 0; i < text.length(); i++) {
                var c = text.charAt(i);
                switch (c) {
                    case '\r' -> { /* ignore; '\n' drives the newline */ }
                    case '\n' -> postKey((short) 0x24, 0); // kVK_Return
                    case '\t' -> postKey((short) 0x30, 0); // kVK_Tab
                    default -> postChar(c);
                }
            }
        } catch (Throwable e) { // UnsatisfiedLinkError if the frameworks are somehow missing
            log.error("Unable to type text on macOS", e);
        }
    }

    @Override
    public void sendMediaKey(VolumeButton button, List<String> apps) {
        // Per-window targeting isn't available on macOS; honour a Spotify preference (else Music.app).
        var spotify = apps.stream().anyMatch(a -> a != null && a.regionMatches(true, 0, "spotify", 0, "spotify".length()));
        mediaControl.execute(button, spotify);
    }

    private void warnIfAccessibilityNotGranted() {
        if (!OsxPermissionHelper.isAccessibilityGranted() && accessibilityWarned.compareAndSet(false, true)) {
            log.warn("Keystrokes require Accessibility permission: System Settings > Privacy & Security > Accessibility > enable PCPanel");
        }
    }

    private static void postChar(char c) {
        var cg = CoreGraphics.INSTANCE;
        var chars = new short[] { (short) c };
        var down = cg.CGEventCreateKeyboardEvent(null, (short) 0, true);
        if (down != null) {
            cg.CGEventKeyboardSetUnicodeString(down, new NativeLong(1), chars);
            cg.CGEventPost(K_CG_HID_EVENT_TAP, down);
            CoreFoundation.INSTANCE.CFRelease(down);
        }
        var up = cg.CGEventCreateKeyboardEvent(null, (short) 0, false);
        if (up != null) {
            cg.CGEventKeyboardSetUnicodeString(up, new NativeLong(1), chars);
            cg.CGEventPost(K_CG_HID_EVENT_TAP, up);
            CoreFoundation.INSTANCE.CFRelease(up);
        }
    }

    private static void postKey(short keyCode, long flags) {
        var cg = CoreGraphics.INSTANCE;
        var down = cg.CGEventCreateKeyboardEvent(null, keyCode, true);
        if (down != null) {
            cg.CGEventSetFlags(down, flags);
            cg.CGEventPost(K_CG_HID_EVENT_TAP, down);
            CoreFoundation.INSTANCE.CFRelease(down);
        }
        var up = cg.CGEventCreateKeyboardEvent(null, keyCode, false);
        if (up != null) {
            cg.CGEventSetFlags(up, flags);
            cg.CGEventPost(K_CG_HID_EVENT_TAP, up);
            CoreFoundation.INSTANCE.CFRelease(up);
        }
    }

    static long modifierFlag(String mod) {
        return switch (mod) {
            case "ctrl" -> FLAG_CONTROL;
            case "shift" -> FLAG_SHIFT;
            case "alt" -> FLAG_ALTERNATE;
            case "cmd", "command", "windows", "meta" -> FLAG_COMMAND;
            default -> 0;
        };
    }

    static short keyCode(String token) {
        return KEY_CODES.getOrDefault(token.toUpperCase(), UNKNOWN);
    }

    @SuppressWarnings("java:S138") // long but flat lookup table
    private static Map<String, Short> buildKeyCodes() {
        Map<String, Short> m = new HashMap<>();
        // Letters (kVK_ANSI_*)
        m.put("A", (short) 0x00); m.put("S", (short) 0x01); m.put("D", (short) 0x02); m.put("F", (short) 0x03);
        m.put("H", (short) 0x04); m.put("G", (short) 0x05); m.put("Z", (short) 0x06); m.put("X", (short) 0x07);
        m.put("C", (short) 0x08); m.put("V", (short) 0x09); m.put("B", (short) 0x0B); m.put("Q", (short) 0x0C);
        m.put("W", (short) 0x0D); m.put("E", (short) 0x0E); m.put("R", (short) 0x0F); m.put("Y", (short) 0x10);
        m.put("T", (short) 0x11); m.put("O", (short) 0x1F); m.put("U", (short) 0x20); m.put("I", (short) 0x22);
        m.put("P", (short) 0x23); m.put("L", (short) 0x25); m.put("J", (short) 0x26); m.put("K", (short) 0x28);
        m.put("N", (short) 0x2D); m.put("M", (short) 0x2E);
        // Digits (top row)
        m.put("1", (short) 0x12); m.put("2", (short) 0x13); m.put("3", (short) 0x14); m.put("4", (short) 0x15);
        m.put("5", (short) 0x17); m.put("6", (short) 0x16); m.put("7", (short) 0x1A); m.put("8", (short) 0x1C);
        m.put("9", (short) 0x19); m.put("0", (short) 0x1D);
        // Punctuation (AWT VK names)
        m.put("EQUALS", (short) 0x18); m.put("MINUS", (short) 0x1B); m.put("OPEN_BRACKET", (short) 0x21);
        m.put("CLOSE_BRACKET", (short) 0x1E); m.put("QUOTE", (short) 0x27); m.put("SEMICOLON", (short) 0x29);
        m.put("BACK_SLASH", (short) 0x2A); m.put("COMMA", (short) 0x2B); m.put("SLASH", (short) 0x2C);
        m.put("PERIOD", (short) 0x2F); m.put("BACK_QUOTE", (short) 0x32);
        // Control / navigation
        m.put("ENTER", (short) 0x24); m.put("TAB", (short) 0x30); m.put("SPACE", (short) 0x31);
        m.put("BACK_SPACE", (short) 0x33); m.put("ESCAPE", (short) 0x35); m.put("ESC", (short) 0x35);
        m.put("DELETE", (short) 0x75); m.put("HOME", (short) 0x73); m.put("END", (short) 0x77);
        m.put("PAGE_UP", (short) 0x74); m.put("PAGE_DOWN", (short) 0x79);
        m.put("LEFT", (short) 0x7B); m.put("RIGHT", (short) 0x7C); m.put("DOWN", (short) 0x7D); m.put("UP", (short) 0x7E);
        // Function keys
        m.put("F1", (short) 0x7A); m.put("F2", (short) 0x78); m.put("F3", (short) 0x63); m.put("F4", (short) 0x76);
        m.put("F5", (short) 0x60); m.put("F6", (short) 0x61); m.put("F7", (short) 0x62); m.put("F8", (short) 0x64);
        m.put("F9", (short) 0x65); m.put("F10", (short) 0x6D); m.put("F11", (short) 0x67); m.put("F12", (short) 0x6F);
        return m;
    }
}
