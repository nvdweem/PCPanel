package com.getpcpanel.sleepdetection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.lightshow.LightShow.Layout;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.device.provider.pcpanel.OutputInterpreter;
import com.getpcpanel.integration.device.BrightnessService;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.util.coloroverride.OverrideColorService;

import jakarta.enterprise.event.Event;

/** The lights a dark panel shows while the PC is locked but awake: the features allowed to keep going, or nothing. */
class SleepDetectorDarkFrameTest {
    private static final String SERIAL = "pro";
    private final List<String> order = new CopyOnWriteArrayList<>();
    private final List<LightingConfig> frames = new CopyOnWriteArrayList<>();
    private ExecutorService writes;
    private SleepDetector sut;
    private LightingConfig base;
    private Save save;
    private volatile boolean overrides;
    private volatile String knobColor = "#ff0000";
    private volatile OptionalInt runtimeBrightness = OptionalInt.empty();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        order.clear();
        frames.clear();
        overrides = false;
        base = LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, (byte) 100, false);
        base.setGlobalBrightness(40);
        var device = mock(Device.class);
        when(device.deviceType()).thenReturn(DeviceType.PCPANEL_PRO);
        when(device.getSerialNumber()).thenReturn(SERIAL);
        when(device.lightingConfig()).thenReturn(base);
        when(device.descriptor()).thenReturn(DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        writes = Executors.newSingleThreadExecutor();
        sut = new SleepDetector(writes);
        sut.devices = mock(DeviceHolder.class);
        when(sut.devices.values()).thenReturn(List.of(device));
        when(sut.devices.getDevice(SERIAL)).thenReturn(Optional.of(device));
        save = new Save();
        sut.saveService = mock(SaveService.class);
        when(sut.saveService.get()).thenReturn(save);
        sut.overrideColorService = mock(OverrideColorService.class);
        when(sut.overrideColorService.anyOverride(eq(SERIAL), any(Layout.class))).thenAnswer(i -> overrides);
        when(sut.overrideColorService.getDialOverride(eq(SERIAL), org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> overrides && (int) i.getArgument(1) == 0
                ? Optional.of(new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(knobColor)) : Optional.empty());
        when(sut.overrideColorService.getSliderOverride(eq(SERIAL), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(sut.overrideColorService.getSliderLabelOverride(eq(SERIAL), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        when(sut.overrideColorService.getLogoOverride(SERIAL)).thenReturn(Optional.empty());
        sut.brightnessService = mock(BrightnessService.class);
        when(sut.brightnessService.runtimeBrightness(SERIAL)).thenAnswer(i -> runtimeBrightness);
        sut.outputInterpreter = mock(OutputInterpreter.class);
        doAnswer(inv -> {
            LightingConfig lc = inv.getArgument(2);
            if (lc == base) {
                order.add("relight");
            } else if (lc.lightingMode() == LightingMode.CUSTOM) {
                frames.add(lc);
                order.add("frame");
            } else {
                order.add("off");
            }
            return null;
        }).when(sut.outputInterpreter).sendLightingConfig(eq(SERIAL), any(), any(), anyBoolean());
        sut.panelsDark = mock(Event.class);
        doAnswer(inv -> {
            var e = (PanelsDarkEvent) inv.getArgument(0);
            return order.add(!e.dark() ? "lit" : e.awake() ? "dark" : "asleep");
        }).when(sut.panelsDark).fire(any());
    }

    @AfterEach
    void tearDown() {
        writes.shutdownNow();
    }

    private void flush() throws Exception {
        writes.submit(() -> { }).get();
    }

    private void event(SystemEventType type) {
        sut.onEvent(new SystemEvent(type));
    }

    @Test
    void aLockIsAnnouncedAsDarkButAwakeAndASuspendAsAsleep() throws Exception {
        event(SystemEventType.locked);
        event(SystemEventType.displayOff);
        event(SystemEventType.goingToSuspend);
        event(SystemEventType.resumedFromSuspend);
        flush();
        assertEquals(List.of("dark", "off", "asleep", "off", "relight", "lit"), order);
    }

    @Test
    void withTheTogglesOffALockedPanelIsSwitchedOffAsBefore() throws Exception {
        overrides = true;
        event(SystemEventType.locked);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "off"), order);
        assertFalse(sut.showsDarkFrames());
    }

    @Test
    void aLockWithSomethingToShowSendsTheDarkFrameInsteadOfAllOff() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        overrides = true;
        event(SystemEventType.locked);
        flush();
        assertEquals(List.of("dark", "frame"), order);
        assertTrue(sut.showsDarkFrames());
        var frame = frames.getFirst();
        assertEquals(40, frame.getGlobalBrightness(), "the panel's brightness");
        assertEquals(5, frame.knobConfigs().length);
        for (var knob : frame.knobConfigs()) {
            assertEquals(SINGLE_KNOB_MODE.STATIC, knob.getMode(), "black, not unset: the base layer must not fill it");
            assertEquals("#000000", knob.getColor1());
        }
        for (var slider : frame.sliderConfigs()) {
            assertEquals(SINGLE_SLIDER_MODE.STATIC, slider.getMode());
            assertEquals("#000000", slider.getColor1());
        }
        for (var label : frame.sliderLabelConfigs()) {
            assertEquals(SINGLE_SLIDER_LABEL_MODE.STATIC, label.getMode());
            assertEquals("#000000", label.getColor());
        }
        assertEquals(SINGLE_LOGO_MODE.STATIC, frame.logoConfig().getMode());
        assertEquals("#000000", frame.logoConfig().getColor());
    }

    @Test
    void whenTheLastThingStopsShowingThePanelGoesOffOnce() throws Exception {
        save.setVisualizerWhileLocked(true);
        event(SystemEventType.locked);
        flush();
        overrides = true;
        sut.showDarkFrame(SERIAL);
        flush();
        overrides = false;
        sut.showDarkFrame(SERIAL);
        flush();
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "off", "frame", "off"), order);
    }

    @Test
    void switchingTheToggleOffWhileShowingSwitchesThePanelOff() throws Exception {
        save.setVisualizerWhileLocked(true);
        overrides = true;
        event(SystemEventType.locked);
        flush();
        save.setVisualizerWhileLocked(false);
        sut.onSaveChanged(null);
        flush();
        assertEquals(List.of("dark", "frame", "off"), order);
    }

    @Test
    void nothingIsShownWhileLitOrAsleep() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        overrides = true;
        sut.showDarkFrame(SERIAL);
        event(SystemEventType.goingToSuspend);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("asleep", "off"), order);
        assertFalse(sut.showsDarkFrames());
    }

    @Test
    void suspendingWhileShowingSwitchesOffAndStopsShowing() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        overrides = true;
        event(SystemEventType.locked);
        flush();
        event(SystemEventType.goingToSuspend);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "frame", "asleep", "off"), order);
    }

    @Test
    void aDarkFrameAskedForAfterTheWakeIsNotSent() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        overrides = true;
        event(SystemEventType.locked);
        flush();
        event(SystemEventType.unlocked);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "frame", "relight", "lit"), order);
    }

    @Test
    void nothingIsShownAfterShutdown() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        overrides = true;
        event(SystemEventType.locked);
        flush();
        sut.onShutdown(null);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "frame", "off"), order);
    }

    private void lockedWithNothingShowing() throws Exception {
        save.setNotificationLightsWhileLocked(true);
        event(SystemEventType.locked);
        flush();
    }

    @Test
    void aPanelThatReconnectsWhileLockedIsSwitchedOff() throws Exception {
        lockedWithNothingShowing();
        sut.panelChanged(SERIAL); // the init on connect: the panel shows its power-on lighting
        sut.showDarkFrame(SERIAL); // the connect's own lighting, intercepted
        flush();
        assertEquals(List.of("dark", "off", "off"), order);
    }

    @Test
    void theRelightAfterALightShowWhileLockedSwitchesThePanelOff() throws Exception {
        lockedWithNothingShowing();
        sut.panelChanged(SERIAL); // a show frame
        sut.panelChanged(SERIAL);
        sut.showDarkFrame(SERIAL); // the show's final relight, intercepted
        flush();
        assertEquals(List.of("dark", "off", "off"), order);
    }

    @Test
    void anUnchangedDarkFrameIsSentOnce() throws Exception {
        lockedWithNothingShowing();
        overrides = true;
        sut.showDarkFrame(SERIAL);
        flush();
        sut.showDarkFrame(SERIAL); // an audio-level or mute relight, intercepted: nothing visible changed
        flush();
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "off", "frame"), order);
        knobColor = "#00ff00";
        sut.showDarkFrame(SERIAL);
        flush();
        runtimeBrightness = OptionalInt.of(10);
        sut.showDarkFrame(SERIAL);
        flush();
        assertEquals(List.of("dark", "off", "frame", "frame", "frame"), order, "a new colour or brightness is sent");
    }

    @Test
    void aDeviceWithoutLightingGetsAFullBrightnessFrame() {
        var device = mock(Device.class);
        when(device.descriptor()).thenReturn(DescriptorFactory.forType(DeviceType.PCPANEL_PRO));
        assertEquals(100, SleepDetector.darkFrame(device).getGlobalBrightness());
    }
}
