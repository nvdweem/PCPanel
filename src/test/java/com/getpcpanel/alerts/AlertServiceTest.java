package com.getpcpanel.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.NotificationAlert;
import com.getpcpanel.profile.dto.NotificationAlert.AlertEffect;
import com.getpcpanel.profile.dto.NotificationAlert.AlertTrigger;
import com.getpcpanel.sleepdetection.PanelsDarkEvent;
import com.getpcpanel.sleepdetection.SleepDetector;

import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.event.Event;

class AlertServiceTest {
    private static final int PERIOD = 1_000;
    private AlertService sut;
    private Device device;
    private Save save;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        device = mock(Device.class);
        when(device.getSerialNumber()).thenReturn("pro");
        when(device.deviceType()).thenReturn(DeviceType.PCPANEL_PRO);
        when(device.descriptor()).thenReturn(DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        save = new Save();
        save.setNotificationAlerts(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "discord.exe", "knob:0", "#ff0000", true, null, false, null,
                AlertEffect.BLINK, PERIOD, null, null)));
        sut = new AlertService();
        sut.save = mock(SaveService.class);
        when(sut.save.get()).thenReturn(save);
        sut.devices = mock(DeviceHolder.class);
        when(sut.devices.all()).thenReturn(List.of(device));
        sut.micUsage = mock(MicUsage.class);
        sut.notificationWatch = mock(NotificationWatch.class);
        sut.windowTitles = mock(WindowTitles.class);
        sut.alertLighting = mock(AlertLighting.class);
        sut.visualColorsChanged = mock(Event.class);
        sut.alertsLit = mock(Event.class);
        sut.sleep = mock(SleepDetector.class);
    }

    /** Ticks through two blink periods: the light goes on and off four times. */
    private void blink() {
        sut.preview(0);
        for (var now = 0L; now < 2 * PERIOD; now += PERIOD / 2) {
            sut.tick(now);
        }
    }

    @Test
    void aBlinkRelightsThePanelWhileLit() {
        blink();
        verify(device, atLeastOnce()).relight();
    }

    @Test
    void aBlinkSendsNothingWhileThePanelsAreDark() {
        sut.onPanelsDark(new PanelsDarkEvent(true, true));
        blink();
        verify(device, never()).relight();
        verify(device, never()).setLighting(any(), anyBoolean());
        verify(sut.sleep, never()).showDarkFrame(anyString());
    }

    @Test
    void whileLockedWithTheToggleOnABlinkShowsOnTheDarkPanel() {
        save.setNotificationLightsWhileLocked(true);
        sut.onPanelsDark(new PanelsDarkEvent(true, true));
        blink();
        verify(sut.sleep, atLeastOnce()).showDarkFrame("pro");
        verify(device, never()).relight();
    }

    @Test
    void whileAsleepNothingIsShownEvenWithTheToggleOn() {
        save.setNotificationLightsWhileLocked(true);
        sut.onPanelsDark(new PanelsDarkEvent(true, false));
        blink();
        verify(sut.sleep, never()).showDarkFrame(anyString());
        verify(device, never()).relight();
    }

    @Test
    void itShowsOnDarkPanelsOnlyWithTheToggleOn() {
        assertFalse(sut.showsWhileDark());
        save.setNotificationLightsWhileLocked(true);
        assertTrue(sut.showsWhileDark());
    }

    @Test
    void theBlinkRelightsAgainOnceLit() {
        sut.onPanelsDark(new PanelsDarkEvent(true, true));
        blink();
        sut.onPanelsDark(new PanelsDarkEvent(false, true));
        sut.tick(2L * PERIOD); // the light comes on again
        verify(device, atLeastOnce()).relight();
    }

    @Test
    void nothingIsSentAfterShutdown() {
        sut.onShutdown(new ShutdownEvent());
        blink();
        verify(device, never()).relight();
        verify(sut.alertLighting, never()).update(any(), anyBoolean());
    }

    @Test
    void aLitLightCarriesTheAlertsOwnBrightness() {
        save.setNotificationAlerts(List.of(new NotificationAlert(AlertTrigger.TASKBAR_FLASH, "discord.exe", "knob:0", "#ff0000", false, null, false, null,
                AlertEffect.STEADY, PERIOD, null, null, null, 100)));
        sut.preview(0);
        sut.tick(0);
        var override = sut.getOverrideColorProvider().getDialOverride("pro", 0).orElseThrow();
        assertEquals("#ff0000", override.getColor1());
        assertEquals(100, override.getOverrideBrightness());
    }

    @Test
    void aBlinkKeepsItsLightInTheSecondHalf() {
        sut.preview(0);
        sut.tick(PERIOD / 2);
        var override = sut.getOverrideColorProvider().getDialOverride("pro", 0).orElseThrow();
        assertEquals("#000000", override.getColor1());
        assertNull(override.getOverrideBrightness(), "follows the panel");
    }

    @Test
    void aFailureToReadWindowTitlesIsReportedNotThrown() {
        var save = new Save();
        save.setNotificationAlerts(List.of(new NotificationAlert(AlertTrigger.WINDOW_TITLE, "", "knob:0", "#ff0000", false, null, false, null,
                null, null, "ringing", null)));
        when(sut.save.get()).thenReturn(save);
        sut.tick(0);
        when(sut.windowTitles.titles()).thenThrow(new IllegalStateException("no windows")).thenReturn(Map.of("teams", List.of("Call ringing")));

        assertTrue(sut.pollTitlesOnce(false), "still reading");
        assertEquals("java.lang.IllegalStateException: no windows", sut.titleFailure, "reported, once at warning level");
        assertTrue(sut.pollTitlesOnce(true));
        assertNull(sut.titleFailure, "a good read clears it");
    }
}
