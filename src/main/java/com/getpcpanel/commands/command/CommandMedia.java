package com.getpcpanel.commands.command;

import java.util.Optional;

import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandMedia extends Command implements ButtonAction {
    private final VolumeButton button;

    @RequiredArgsConstructor
    public enum VolumeButton {
        mute(0xAD),
        next(0xB0),
        prev(0xB1),
        stop(0xB2),
        playPause(0xB3);

        private final int key;

        public static Optional<VolumeButton> tryValueOf(String name) {
            try {
                return Optional.of(valueOf(name));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    public CommandMedia(VolumeButton button) {
        this.button = button;
    }

    @Override
    public void execute() {
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
}
