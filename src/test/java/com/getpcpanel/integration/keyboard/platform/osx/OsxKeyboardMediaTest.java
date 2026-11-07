package com.getpcpanel.integration.keyboard.platform.osx;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.getpcpanel.util.os.OsxPermissionHelper;

class OsxKeyboardMediaTest {
    @ParameterizedTest
    @EnumSource(value = VolumeButton.class, names = {"playPause", "next", "prev"})
    void defaultMediaCommandsUseSystemEvents(VolumeButton button) {
        var keyboard = new OsxKeyboard();
        keyboard.systemMediaKeys = mock(OsxSystemMediaKeys.class);
        keyboard.mediaControl = mock(OsxMediaControl.class);
        try (var permissions = mockStatic(OsxPermissionHelper.class)) {
            permissions.when(OsxPermissionHelper::isAccessibilityGranted).thenReturn(true);
            keyboard.sendMediaKey(button, List.of());
        }
        verify(keyboard.systemMediaKeys).send(button);
        verifyNoInteractions(keyboard.mediaControl);
    }

    @ParameterizedTest
    @EnumSource(value = VolumeButton.class, names = {"playPause", "next", "prev"})
    void withoutAccessibilityDefaultMediaCommandsControlMusic(VolumeButton button) {
        var keyboard = new OsxKeyboard();
        keyboard.systemMediaKeys = mock(OsxSystemMediaKeys.class);
        keyboard.mediaControl = mock(OsxMediaControl.class);
        try (var permissions = mockStatic(OsxPermissionHelper.class)) {
            permissions.when(OsxPermissionHelper::isAccessibilityGranted).thenReturn(false);
            keyboard.sendMediaKey(button, List.of());
        }
        verify(keyboard.mediaControl).execute(button, false);
        verifyNoInteractions(keyboard.systemMediaKeys);
    }

    @ParameterizedTest
    @EnumSource(value = VolumeButton.class)
    void explicitSpotifyRoutingIsUnchanged(VolumeButton button) {
        var keyboard = new OsxKeyboard();
        keyboard.systemMediaKeys = mock(OsxSystemMediaKeys.class);
        keyboard.mediaControl = mock(OsxMediaControl.class);
        keyboard.sendMediaKey(button, List.of("other.exe", "sPoTiFy.exe"));
        verify(keyboard.mediaControl).execute(button, true);
        verifyNoInteractions(keyboard.systemMediaKeys);
    }

    @ParameterizedTest
    @EnumSource(value = VolumeButton.class, names = {"stop", "mute"})
    void legacyStopAndMuteHandlingIsUnchanged(VolumeButton button) {
        var keyboard = new OsxKeyboard();
        keyboard.systemMediaKeys = mock(OsxSystemMediaKeys.class);
        keyboard.mediaControl = mock(OsxMediaControl.class);
        keyboard.sendMediaKey(button, List.of());
        verify(keyboard.mediaControl).execute(button, false);
        verifyNoInteractions(keyboard.systemMediaKeys);
    }
}
