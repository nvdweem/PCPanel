package com.getpcpanel.cpp.windows;

import java.util.HashMap;
import java.util.Map;

import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import lombok.extern.log4j.Log4j2;

/**
 * Synthesises keystrokes on Windows through {@code User32.SendInput} (the same Win32 path
 * {@link com.getpcpanel.commands.command.CommandMedia} uses for media keys), replacing
 * {@link java.awt.Robot} so the native image never needs the AWT windowing toolkit.
 *
 * <p>The input string is the cross-platform "{@code modifier+modifier+key}" format parsed by
 * {@link com.getpcpanel.commands.KeyMacro}; tokens are the AWT {@code VK_}-suffix names. They are
 * mapped here to Win32 virtual-key codes and posted as keydown/keyup {@code KEYBDINPUT} events.
 */
@Log4j2
public final class WindowsKeyboard {
    private static final int KEYEVENTF_KEYUP = 0x0002;

    /** AWT-VK-style token (the part after {@code VK_}) → Win32 virtual-key code, for non-trivial keys. */
    private static final Map<String, Integer> KEY_CODES = buildKeyCodes();

    private WindowsKeyboard() {
    }

    public static void executeKeyStroke(String input) {
        if (input == null || input.contains("UNDEFINED")) {
            return;
        }
        var parts = input.replace(" ", "").split("\\+");
        var pressed = new java.util.ArrayList<Integer>();
        try {
            for (var i = 0; i < parts.length - 1; i++) {
                var vk = modifierVk(parts[i]);
                if (vk == 0) {
                    log.error("bad keystroke modifier: {}", parts[i]);
                } else {
                    sendVk(vk, false);
                    pressed.add(vk);
                }
            }
            var keyVk = keyVk(parts[parts.length - 1]);
            if (keyVk == 0) {
                log.error("Unsupported Windows keystroke key '{}' in '{}'", parts[parts.length - 1], input);
            } else {
                sendVk(keyVk, false);
                pressed.add(keyVk);
            }
        } catch (Throwable e) { // UnsatisfiedLinkError if user32 is somehow missing
            log.error("Unable to post Windows keystroke '{}'", input, e);
        } finally {
            // Release in reverse order so a half-finished combo never leaves a modifier stuck.
            for (var i = pressed.size() - 1; i >= 0; i--) {
                try {
                    sendVk(pressed.get(i), true);
                } catch (Throwable e) {
                    log.error("Unable to release key {}", pressed.get(i), e);
                }
            }
        }
    }

    private static void sendVk(int vk, boolean keyUp) {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki.wVk = new WinDef.WORD(vk);
        input.input.ki.dwFlags = new WinDef.DWORD(keyUp ? KEYEVENTF_KEYUP : 0);
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }

    private static int modifierVk(String mod) {
        return switch (mod) {
            case "ctrl" -> 0x11;                       // VK_CONTROL
            case "shift" -> 0x10;                       // VK_SHIFT
            case "alt" -> 0x12;                         // VK_MENU
            case "cmd", "command", "windows", "meta" -> 0x5B; // VK_LWIN
            default -> 0;
        };
    }

    private static int keyVk(String token) {
        var t = token.toUpperCase();
        if (t.length() == 1) {
            var c = t.charAt(0);
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
                return c; // VK_A..VK_Z == 'A'..'Z', VK_0..VK_9 == '0'..'9'
            }
        }
        return KEY_CODES.getOrDefault(t, 0);
    }

    @SuppressWarnings("java:S138") // long but flat lookup table
    private static Map<String, Integer> buildKeyCodes() {
        Map<String, Integer> m = new HashMap<>();
        // Function keys
        for (var i = 1; i <= 12; i++) {
            m.put("F" + i, 0x70 + (i - 1)); // VK_F1..VK_F12
        }
        // Control / navigation
        m.put("ENTER", 0x0D); m.put("TAB", 0x09); m.put("SPACE", 0x20);
        m.put("BACK_SPACE", 0x08); m.put("ESCAPE", 0x1B); m.put("ESC", 0x1B);
        m.put("DELETE", 0x2E); m.put("INSERT", 0x2D); m.put("HOME", 0x24); m.put("END", 0x23);
        m.put("PAGE_UP", 0x21); m.put("PAGE_DOWN", 0x22);
        m.put("LEFT", 0x25); m.put("UP", 0x26); m.put("RIGHT", 0x27); m.put("DOWN", 0x28);
        // Punctuation (OEM keys, US layout)
        m.put("MINUS", 0xBD); m.put("EQUALS", 0xBB); m.put("OPEN_BRACKET", 0xDB);
        m.put("CLOSE_BRACKET", 0xDD); m.put("BACK_SLASH", 0xDC); m.put("SEMICOLON", 0xBA);
        m.put("QUOTE", 0xDE); m.put("COMMA", 0xBC); m.put("PERIOD", 0xBE); m.put("SLASH", 0xBF);
        m.put("BACK_QUOTE", 0xC0);
        return m;
    }
}
