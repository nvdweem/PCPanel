package com.getpcpanel.commands.command;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.windows.SndCtrlWindows;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import one.util.streamex.StreamEx;

/**
 * Spotify logic from <a href="https://github.com/moobsmc/spotify-controls/blob/master/Spotify.cpp">Spotify-Controls</a>
 */
@Getter
@ToString(callSuper = true)
public class CommandMedia extends Command implements ButtonAction {
    private static final int WM_APPCOMMAND = 0x0319;
    private final VolumeButton button;
    private final boolean spotify;

    @RequiredArgsConstructor
    public enum VolumeButton {
        mute(0xAD, 0x80000),
        next(0xB0, 0xB0000),
        prev(0xB1, 0xC0000),
        stop(0xB2, 0xD0000),
        playPause(0xB3, 0xE0000);

        private final int key;
        private final int spotify;

        public static Optional<VolumeButton> tryValueOf(String name) {
            try {
                return Optional.of(valueOf(name));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    @JsonCreator
    public CommandMedia(@JsonProperty("button") VolumeButton button, @JsonProperty("spotify") boolean spotify) {
        this.button = button;
        this.spotify = spotify;
    }

    @Override
    public void execute() {
        if (spotify) {
            executeSpotify();
        } else {
            executeGlobalMedia();
        }
    }

    private void executeGlobalMedia() {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wScan = new WinDef.WORD(0);
        input.input.ki.time = new WinDef.DWORD(0);
        input.input.ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki.wVk = new WinDef.WORD(button.key);
        input.input.ki.dwFlags = new WinDef.DWORD(0);  // keydown

        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
        input.input.ki.dwFlags = new WinDef.DWORD(2);  // keyup
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), (WinUser.INPUT[]) input.toArray(1), input.size());
    }

    private void executeSpotify() {
        var spotifyWnd = findSpotify();
        User32.INSTANCE.SendMessage(spotifyWnd, WM_APPCOMMAND, new WinDef.WPARAM(0), new WinDef.LPARAM(button.spotify));
    }

    private WinDef.HWND findSpotify() {
        var result = new WinDef.HWND[] { null };
        var pidIsSpotify = StreamEx.of(MainFX.getBean(SndCtrlWindows.class).getRunningApplications()).mapToEntry(ISndCtrl.RunningApplication::pid, ra -> StringUtils.equalsIgnoreCase("spotify.exe", ra.file().getName())).distinctKeys().toMap();
        User32.INSTANCE.EnumWindows((hWnd, data) -> {
            var target = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, target);

            if (pidIsSpotify.getOrDefault(target.getValue(), false)) {
                result[0] = hWnd;
                return false;
            }
            return true;
        }, null);
        return result[0];
    }
}
