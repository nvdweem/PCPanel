package com.getpcpanel.integration.keyboard.platform.osx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.sun.jna.Pointer;

class OsxSystemMediaKeysTest {
    @ParameterizedTest
    @CsvSource({"playPause,16,0x100A00,0x100B00", "next,19,0x130A00,0x130B00", "prev,20,0x140A00,0x140B00"})
    void physicalMediaKeyPayloads(VolumeButton button, int code, String down, String up) {
        assertEquals(code, OsxSystemMediaKeys.keyCode(button));
        assertEquals(Long.decode(down), OsxSystemMediaKeys.data1(code, true));
        assertEquals(Long.decode(up), OsxSystemMediaKeys.data1(code, false));
    }

    @Test
    void completePairIsPreparedBeforePostingAndPoolOutlivesBothPosts() {
        var events = mock(OsxSystemMediaKeys.Events.class);
        var pool = Pointer.createConstant(1);
        var down = Pointer.createConstant(2);
        var up = Pointer.createConstant(3);
        var downCg = Pointer.createConstant(4);
        var upCg = Pointer.createConstant(5);
        when(events.pushPool()).thenReturn(pool);
        when(events.create(16, true)).thenReturn(down);
        when(events.create(16, false)).thenReturn(up);
        when(events.cgEvent(down)).thenReturn(downCg);
        when(events.cgEvent(up)).thenReturn(upCg);

        new OsxSystemMediaKeys(events).send(VolumeButton.playPause);

        var ordered = inOrder(events);
        ordered.verify(events).pushPool();
        ordered.verify(events).create(16, true);
        ordered.verify(events).create(16, false);
        ordered.verify(events).cgEvent(down);
        ordered.verify(events).cgEvent(up);
        ordered.verify(events).post(downCg);
        ordered.verify(events).post(upCg);
        ordered.verify(events).popPool(pool);
    }

    @Test
    void failedReleaseConstructionDoesNotPostAPartialPressAndReleasesPool() {
        var events = mock(OsxSystemMediaKeys.Events.class);
        var pool = Pointer.createConstant(1);
        when(events.pushPool()).thenReturn(pool);
        when(events.create(16, true)).thenReturn(Pointer.createConstant(2));

        assertThrows(IllegalStateException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.playPause));

        verify(events, never()).post(any());
        verify(events).popPool(pool);
    }

    @Test
    void failedCgConversionDoesNotPostAPartialPressAndReleasesPool() {
        var events = mock(OsxSystemMediaKeys.Events.class);
        var pool = Pointer.createConstant(1);
        var down = Pointer.createConstant(2);
        var up = Pointer.createConstant(3);
        when(events.pushPool()).thenReturn(pool);
        when(events.create(16, true)).thenReturn(down);
        when(events.create(16, false)).thenReturn(up);
        when(events.cgEvent(down)).thenReturn(Pointer.createConstant(4));

        assertThrows(IllegalStateException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.playPause));

        verify(events, never()).post(any());
        verify(events).popPool(pool);
    }

    @Test
    void unsupportedActionDoesNotLoadNativeFrameworks() {
        var events = mock(OsxSystemMediaKeys.Events.class);
        assertThrows(IllegalArgumentException.class, () -> new OsxSystemMediaKeys(events).send(VolumeButton.stop));
        verifyNoInteractions(events);
    }
}
