package com.getpcpanel.commands.command;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Spotify logic from <a href="https://github.com/moobsmc/spotify-controls/blob/master/Spotify.cpp">Spotify-Controls</a>
 */
@Getter
@ToString(callSuper = true)
public class CommandMedia extends Command implements ButtonAction {
    private static final int WM_APPCOMMAND = 0x0319;
    public static final String SPOTIFY_HANDLE = "Chrome_WidgetWin_0";

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
        var window = User32.INSTANCE.FindWindow(SPOTIFY_HANDLE, null);
        User32.INSTANCE.SendMessage(window, WM_APPCOMMAND, new WinDef.WPARAM(0), new WinDef.LPARAM(button.spotify));
    }
}
