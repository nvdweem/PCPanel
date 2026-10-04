package com.getpcpanel.device.provider.pcpanel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.integration.visualizer.VisualizerService;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.sleepdetection.SleepDetector;
import com.getpcpanel.util.coloroverride.OverrideColorService;

/** While the panels show dark frames, a device's own lighting shows as its dark frame instead. */
class OutputInterpreterDarkTest {
    private OutputInterpreter sut;
    private final LightingConfig lighting = LightingConfig.createAllColor("#ff0000");

    @BeforeEach
    void setUp() {
        sut = new OutputInterpreter();
        sut.deviceScanner = mock(DeviceScanner.class);
        sut.overrideColorService = mock(OverrideColorService.class);
        sut.baseLayer = mock(BaseLayerService.class);
        sut.brightnessService = mock(BrightnessService.class);
        sut.visualizer = mock(VisualizerService.class);
        sut.sleep = mock(SleepDetector.class);
        when(sut.visualizer.substitute(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(sut.baseLayer.effectiveLighting(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(sut.brightnessService.runtimeBrightness(anyString())).thenReturn(java.util.OptionalInt.empty());
        when(sut.deviceScanner.getConnectedDevice(anyString())).thenReturn(mock(DeviceCommunicationHandler.class));
    }

    @Test
    void whileShowingDarkFramesTheDarkFrameIsShownInstead() {
        when(sut.sleep.showsDarkFrames()).thenReturn(true);
        sut.sendDeviceLighting("pro", DeviceType.PCPANEL_PRO, lighting, true);
        verify(sut.sleep).showDarkFrame("pro");
        verifyNoInteractions(sut.deviceScanner);
    }

    @Test
    void otherwiseTheLightingIsSent() {
        sut.sendDeviceLighting("pro", DeviceType.PCPANEL_PRO, lighting, true);
        verify(sut.sleep, never()).showDarkFrame(anyString());
        verify(sut.deviceScanner).getConnectedDevice("pro");
    }

    @Test
    void lightingThatOverrulesTheDeviceIsNeverReplaced() {
        when(sut.sleep.showsDarkFrames()).thenReturn(true);
        sut.sendLightingConfig("pro", DeviceType.PCPANEL_PRO, lighting, true);
        verify(sut.sleep, never()).showDarkFrame(anyString());
        verify(sut.deviceScanner).getConnectedDevice("pro");
    }

    @Test
    void theInitOnConnectTellsTheSleepDetectorThePanelChanged() {
        sut.sendInit("pro");
        verify(sut.sleep).panelChanged("pro");
    }

    @Test
    void aTemporaryFrameTellsTheSleepDetectorThePanelChanged() {
        sut.sendTemporaryLighting("pro", DeviceType.PCPANEL_PRO, lighting);
        verify(sut.sleep).panelChanged("pro");
        verify(sut.deviceScanner).getConnectedDevice("pro");
    }
}
