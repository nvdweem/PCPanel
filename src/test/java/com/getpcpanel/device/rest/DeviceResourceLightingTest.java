package com.getpcpanel.device.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;

import jakarta.enterprise.event.Event;

class DeviceResourceLightingTest {
    private DeviceResource sut;
    private Device device;
    private LightingConfig saved;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        saved = LightingConfig.createAllColor("#FFFFFF");
        saved.setGlobalBrightness(60);
        device = mock(Device.class);
        when(device.getSavedLightingConfig()).thenAnswer(i -> saved);
        sut = new DeviceResource();
        sut.deviceHolder = mock(DeviceHolder.class);
        when(sut.deviceHolder.getDevice("s")).thenReturn(Optional.of(device));
        sut.saveService = mock(SaveService.class);
        sut.eventBus = mock(Event.class);
        sut.brightnessService = new BrightnessService();
        sut.brightnessService.setButtonBrightness("s", 20);
    }

    @Test
    void movingTheBrightnessSliderTakesOverFromAButton() {
        var moved = LightingConfig.createAllColor("#FFFFFF");
        moved.setGlobalBrightness(80);

        var atRelight = new AtomicReference<OptionalInt>();
        doAnswer(i -> {
            atRelight.set(sut.brightnessService.runtimeBrightness("s"));
            return null;
        }).when(device).setSavedLighting(any());

        sut.setLighting("s", moved);

        assertEquals(OptionalInt.empty(), atRelight.get(), "the panel is relit at the slider's brightness, not the button's");
        assertEquals(OptionalInt.empty(), sut.brightnessService.runtimeBrightness("s"));
    }

    @Test
    void otherLightingChangesKeepTheButtonsBrightness() {
        var recoloured = LightingConfig.createAllColor("#FFFFFF");
        recoloured.setGlobalBrightness(60);

        sut.setLighting("s", recoloured);

        assertEquals(OptionalInt.of(20), sut.brightnessService.runtimeBrightness("s"));
    }
}
