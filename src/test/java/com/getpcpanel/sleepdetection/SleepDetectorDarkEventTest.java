package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;

import jakarta.enterprise.event.Event;

class SleepDetectorDarkEventTest {
    private final List<String> order = new CopyOnWriteArrayList<>();
    private SleepDetector sut;
    private LightingConfig base;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        order.clear();
        base = LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, (byte) 100, false);
        var device = mock(Device.class);
        when(device.deviceType()).thenReturn(DeviceType.PCPANEL_PRO);
        when(device.getSerialNumber()).thenReturn("pro");
        when(device.lightingConfig()).thenReturn(base);
        sut = new SleepDetector();
        sut.devices = mock(DeviceHolder.class);
        when(sut.devices.values()).thenReturn(List.of(device));
        sut.saveService = mock(SaveService.class);
        when(sut.saveService.get()).thenReturn(new Save());
        sut.outputInterpreter = mock(OutputInterpreter.class);
        doAnswer(inv -> order.add(inv.getArgument(2) == base ? "relight" : "off"))
                .when(sut.outputInterpreter).sendLightingConfig(eq("pro"), any(), any(), anyBoolean());
        sut.panelsDark = mock(Event.class);
        doAnswer(inv -> order.add(((PanelsDarkEvent) inv.getArgument(0)).dark() ? "dark" : "lit")).when(sut.panelsDark).fire(any());
    }

    @Test
    void darkIsAnnouncedBeforeTheLightsGoOffAndLitAfterTheRelight() throws InterruptedException {
        sut.onEvent(new SystemEvent(SystemEventType.locked));
        sut.onEvent(new SystemEvent(SystemEventType.unlocked));
        var until = System.currentTimeMillis() + 2_000;
        while (order.size() < 4 && System.currentTimeMillis() < until) {
            Thread.sleep(5);
        }
        assertEquals(List.of("dark", "off", "relight", "lit"), order);
    }
}
