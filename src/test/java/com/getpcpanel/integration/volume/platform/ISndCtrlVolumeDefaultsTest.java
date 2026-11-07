package com.getpcpanel.integration.volume.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ISndCtrlVolumeDefaultsTest {
    @Test
    void groupedVolumeRetainsOrderedScalarCallsOnOtherPlatforms() {
        var snd = mock(ISndCtrl.class, CALLS_REAL_METHODS);
        snd.setProcessesVolume(List.of("Spotify", "Firefox"), "output", 0.4f);
        var ordered = inOrder(snd);
        ordered.verify(snd).setProcessVolume("Spotify", "output", 0.4f);
        ordered.verify(snd).setProcessVolume("Firefox", "output", 0.4f);
    }

    @Test
    void focusDefaultReadsRoutingNameAndDefersExistingPlatformOperation() {
        var snd = mock(ISndCtrl.class, CALLS_REAL_METHODS);
        when(snd.getFocusApplication()).thenReturn("Spotify");
        var focus = snd.focusedVolume(0.4f);
        assertEquals("Spotify", focus.application());
        verify(snd, never()).setFocusVolume(anyFloat());
        focus.apply().run();
        verify(snd).setFocusVolume(0.4f);
    }
}
