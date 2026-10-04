package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.inject.Instance;

/** What "no volume jumps" reads for a focus-volume dial: the level of the app the dial would change. */
class VolumeCoordinatorFocusLevelTest {
    private final VolumeCoordinatorService sut = new VolumeCoordinatorService();
    private final Save save = new Save();
    private final IFocusRedirector redirector = mock(IFocusRedirector.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sut.sndCtrl = mock(ISndCtrl.class);
        when(sut.sndCtrl.getFocusApplication()).thenReturn("firefox.exe");
        when(sut.sndCtrl.getAllSessions()).thenReturn(List.of(
                new AudioSession(null, 10, new File("Spotify.exe"), "Spotify", null, 0.8f, false),
                new AudioSession(null, 11, new File("firefox.exe"), "Firefox", null, 0.35f, false)));
        sut.focusOverride = mock(FocusVolumeOverrideService.class);
        sut.saveService = mock(SaveService.class);
        when(sut.saveService.get()).thenReturn(save);
        Instance<IFocusRedirector> redirectors = mock(Instance.class);
        Instance.Handle<IFocusRedirector> handle = mock(Instance.Handle.class);
        when(handle.get()).thenReturn(redirector);
        when(redirectors.handlesStream()).thenAnswer(i -> Stream.of(handle));
        sut.focusRedirectors = redirectors;
    }

    @Test
    void theFocusedAppsOwnLevel() {
        assertEquals(0.35f, sut.focusLevel());
    }

    @Test
    void unknownWhenTheDialActsOnSomethingElse() {
        when(sut.focusOverride.controls("firefox.exe")).thenReturn(true);
        assertNull(sut.focusLevel(), "an override rule sends it to other targets");
    }

    @Test
    void unknownWhenARedirectorManagesIt() {
        when(redirector.managesFocusApp("firefox.exe")).thenReturn(true);
        assertNull(sut.focusLevel(), "e.g. Wave Link moves its channel instead");
    }

    @Test
    void unknownWithoutAFocusedAppWithSound() {
        when(sut.sndCtrl.getFocusApplication()).thenReturn("explorer.exe");
        assertNull(sut.focusLevel());
    }
}
