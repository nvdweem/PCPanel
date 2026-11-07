package com.getpcpanel.integration.volume;

import static org.mockito.Mockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.inject.Instance;

class VolumeCoordinatorApplyTest {
    private final VolumeCoordinatorService service = new VolumeCoordinatorService();
    private Runnable apply;
    private IFocusRedirector redirector;
    private Save settings;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        apply = mock(Runnable.class);
        redirector = mock(IFocusRedirector.class);
        settings = new Save();
        service.sndCtrl = mock(ISndCtrl.class);
        when(service.sndCtrl.focusedVolume(0.4f)).thenReturn(new ISndCtrl.FocusedVolume("Spotify", apply));
        service.focusOverride = mock(FocusVolumeOverrideService.class);
        service.saveService = mock(SaveService.class);
        when(service.saveService.get()).thenReturn(settings);
        service.focusRedirectors = mock(Instance.class);
        Instance.Handle<IFocusRedirector> handle = mock(Instance.Handle.class);
        when(handle.get()).thenReturn(redirector);
        when(service.focusRedirectors.handlesStream()).thenAnswer(call -> Stream.of(handle));
    }

    @Test
    void fallbackUsesTheResolvedActionWithoutResolvingFocusAgain() {
        service.setFocusVolume(0.4, "panel");
        verify(service.sndCtrl).focusedVolume(0.4f);
        verify(service.sndCtrl, never()).getFocusApplication();
        verify(service.sndCtrl, never()).setFocusVolume(anyFloat());
        verify(apply).run();
    }

    @Test
    void overrideStillWinsWithoutApplyingTheOsAction() {
        when(service.focusOverride.handle("Spotify", 0.4f, "panel")).thenReturn(true);
        service.setFocusVolume(0.4, "panel");
        verifyNoInteractions(redirector, apply);
    }

    @Test
    void redirectorStillHandlesTheSameResolvedApplication() {
        when(redirector.handleFocusVolumeRequest("Spotify", 0.4f)).thenReturn(true);
        service.setFocusVolume(0.4, "panel");
        verifyNoInteractions(apply);
    }

    @Test
    void skipControlledAppsStillPrecedesTheRedirector() {
        settings.setSkipControlledFocusApps(true);
        when(redirector.managesFocusApp("Spotify")).thenReturn(true);
        service.setFocusVolume(0.4, "panel");
        verify(redirector, never()).handleFocusVolumeRequest(anyString(), anyFloat());
        verifyNoInteractions(apply);
    }
}
