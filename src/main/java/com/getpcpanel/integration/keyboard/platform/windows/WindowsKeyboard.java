package com.getpcpanel.integration.keyboard.platform.windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.keyboard.Keyboard;
import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.windows.SndCtrlWindows;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Windows {@link Keyboard} backend: synthesises keystrokes and media keys through
 * {@code User32.SendInput}, replacing {@link java.awt.Robot} so the native image never needs the AWT
 * windowing toolkit.
 *
 * <p>The keystroke input is the cross-platform "{@code modifier+modifier+key}" format; tokens are the
 * AWT {@code VK_}-suffix names, mapped here to Win32 virtual-key codes and posted as keydown/keyup
 * {@code KEYBDINPUT} events. Media keys post the global multimedia virtual key, except Spotify-targeted
 * actions which locate the Spotify window and send it a {@code WM_APPCOMMAND}.
 */
@Log4j2
@ApplicationScoped
@WindowsBuild
class WindowsKeyboard implements Keyboard {
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final int KEYEVENTF_UNICODE = 0x0004;
    private static final int VK_RETURN = 0x0D;
    private static final int VK_TAB = 0x09;
    private static final int WM_APPCOMMAND = 0x0319;

    /** AWT-VK-style token (the part after {@code VK_}) → Win32 virtual-key code, for non-trivial keys. */
    private static final Map<String, Integer> KEY_CODES = buildKeyCodes();

    @Inject
    SndCtrlWindows sndCtrl;

    @Override
    public void executeKeyStroke(String input) {
        if (input == null || input.contains("UNDEFINED")) {
            return;
        }
        var parts = input.replace(" ", "").split("\\+");
        var pressed = new ArrayList<Integer>();
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

    /**
     * Types arbitrary text by posting each character as a {@code KEYEVENTF_UNICODE} event, which is
     * layout-independent (the scan code carries the UTF-16 code unit, so surrogate pairs for
     * astral-plane characters are delivered as their two code units in order). Newlines and tabs are
     * sent as real {@code VK_RETURN}/{@code VK_TAB} presses since the Unicode path does not produce them.
     */
    @Override
    public void typeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            for (var i = 0; i < text.length(); i++) {
                var c = text.charAt(i);
                switch (c) {
                    case '\r' -> { /* ignore; '\n' drives the newline */ }
                    case '\n' -> { sendVk(VK_RETURN, false); sendVk(VK_RETURN, true); }
                    case '\t' -> { sendVk(VK_TAB, false); sendVk(VK_TAB, true); }
                    default -> { sendUnicode(c, false); sendUnicode(c, true); }
                }
            }
        } catch (Throwable e) { // UnsatisfiedLinkError if user32 is somehow missing
            log.error("Unable to type text on Windows", e);
        }
    }

    @Override
    public void sendMediaKey(VolumeButton button, boolean spotify) {
        if (spotify) {
            sendSpotifyAppCommand(button);
        } else {
            sendGlobalMediaKey(button);
        }
    }

    /** Posts the global multimedia virtual key (routed by Windows to the foreground media session). */
    private void sendGlobalMediaKey(VolumeButton button) {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki.wVk = new WinDef.WORD(mediaVk(button));
        input.input.ki.dwFlags = new WinDef.DWORD(0);  // keydown
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
        input.input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_KEYUP);
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }

    /** Sends the media action straight to the Spotify window via {@code WM_APPCOMMAND}. */
    private void sendSpotifyAppCommand(VolumeButton button) {
        var spotifyWnd = findSpotify();
        if (spotifyWnd == null) {
            log.warn("Spotify media command: no Spotify window found, nothing sent");
            return;
        }
        User32.INSTANCE.SendMessage(spotifyWnd, WM_APPCOMMAND, new WinDef.WPARAM(0), new WinDef.LPARAM(appCommand(button)));
    }

    private WinDef.HWND findSpotify() {
        var result = new WinDef.HWND[] { null };
        var apps = sndCtrl.getRunningApplications();
        var pidIsSpotify = StreamEx.of(apps).mapToEntry(ISndCtrl.RunningApplication::pid, ra -> StringUtils.equalsIgnoreCase("spotify.exe", ra.file().getName())).distinctKeys().toMap();
        var spotifyPids = pidIsSpotify.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        var windowsSeen = new int[] { 0 };
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            windowsSeen[0]++;
            var target = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, target);
            if (pidIsSpotify.getOrDefault(target.getValue(), false)) {
                result[0] = hWnd;
                return false;
            }
            return true;
        }, null);
        log.debug("findSpotify: {} running apps, spotify pids={}, windows enumerated={}, match={}", apps.size(), spotifyPids, windowsSeen[0], result[0]);
        return result[0];
    }

    private static void sendUnicode(char c, boolean keyUp) {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wVk = new WinDef.WORD(0);
        input.input.ki.wScan = new WinDef.WORD(c);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_UNICODE | (keyUp ? KEYEVENTF_KEYUP : 0));
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
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

    /** Global multimedia virtual-key code (VK_MEDIA_... / VK_VOLUME_MUTE) for a media button. */
    private static int mediaVk(VolumeButton button) {
        return switch (button) {
            case mute -> 0xAD;       // VK_VOLUME_MUTE
            case next -> 0xB0;       // VK_MEDIA_NEXT_TRACK
            case prev -> 0xB1;       // VK_MEDIA_PREV_TRACK
            case stop -> 0xB2;       // VK_MEDIA_STOP
            case playPause -> 0xB3;  // VK_MEDIA_PLAY_PAUSE
        };
    }

    /** {@code APPCOMMAND_*} lParam (shifted into the high word) for a Spotify {@code WM_APPCOMMAND}. */
    private static int appCommand(VolumeButton button) {
        return switch (button) {
            case mute -> 0x80000;       // APPCOMMAND_VOLUME_MUTE
            case next -> 0xB0000;       // APPCOMMAND_MEDIA_NEXTTRACK
            case prev -> 0xC0000;       // APPCOMMAND_MEDIA_PREVIOUSTRACK
            case stop -> 0xD0000;       // APPCOMMAND_MEDIA_STOP
            case playPause -> 0xE0000;  // APPCOMMAND_MEDIA_PLAY_PAUSE
        };
    }

    static int modifierVk(String mod) {
        return switch (mod) {
            case "ctrl" -> 0x11;                       // VK_CONTROL
            case "shift" -> 0x10;                       // VK_SHIFT
            case "alt" -> 0x12;                         // VK_MENU
            case "cmd", "command", "windows", "meta" -> 0x5B; // VK_LWIN
            default -> 0;
        };
    }

    static int keyVk(String token) {
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
