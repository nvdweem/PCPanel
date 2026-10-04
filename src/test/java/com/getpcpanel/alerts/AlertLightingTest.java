package com.getpcpanel.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.lightshow.LightShow;
import com.getpcpanel.device.provider.pcpanel.DescriptorFactory;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.sleepdetection.PanelsDarkEvent;

import io.quarkus.runtime.ShutdownEvent;

class AlertLightingTest {
    private static final String SHOW_COLOR = "#ABCDEF";
    private AlertLighting sut;
    private FakeShow show;

    @BeforeEach
    void setUp() {
        show = new FakeShow();
        sut = new AlertLighting();
        sut.lightShow = show;
    }

    @AfterEach
    void tearDown() {
        sut.stopAll();
    }

    private static LightingConfig rainbow() {
        return LightingConfig.createRainbowAnimation((byte) 0, (byte) -1, (byte) 100, false);
    }

    private static LightingConfig custom() {
        var lc = new LightingConfig(5, 4).toBuilder().lightingMode(LightingMode.CUSTOM).build();
        lc.knobConfigs()[0] = new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1("#010203");
        return lc;
    }

    @Test
    void litRainbowIsDrawnInSoftwareAndHandedBackWhenDark() throws InterruptedException {
        var base = rainbow();
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);

        sut.update(device, true);
        waitFor(() -> device.frames().size() >= 3, "frames keep coming");
        assertTrue(device.frames().stream().allMatch(lc -> lc.lightingMode() == LightingMode.CUSTOM), "every frame is per-control lighting");
        assertEquals(5, device.frames().get(0).knobConfigs().length);
        assertEquals(4, device.frames().get(0).sliderConfigs().length);
        assertSame(base, device.lightingConfig(), "the device's lighting stays its own while frames show");
        assertEquals(List.of(), device.sends(), "the panel's own lighting is not sent while drawing");

        sut.update(device, false);
        assertEquals(List.of(base), device.sends(), "the panel's own lighting is sent back once");
        var frames = device.frames().size();
        Thread.sleep(200);
        assertEquals(frames, device.frames().size(), "the loop has stopped");
        assertNull(device.painter());
        assertEquals(LightingMode.ALL_RAINBOW, base.lightingMode(), "the device's lighting is never changed");
        assertEquals(0, base.knobConfigs().length, "the device's lighting is never changed");
    }

    @Test
    void solidColourIsSentOnceNotEveryFrame() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, LightingConfig.createAllColor("#123456"));
        sut.update(device, true);
        Thread.sleep(200);
        sut.update(device, true);
        assertEquals(1, device.frames().size(), "a still frame only needs sending once: " + device.frames().size());
        assertEquals("#123456", device.frames().get(0).knobConfigs()[0].getColor1());
    }

    @Test
    void customLightingIsLeftAlone() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, custom());
        sut.update(device, true);
        Thread.sleep(150);
        sut.update(device, false);
        assertEquals(List.of(), device.sent);
    }

    @Test
    void devicesWithoutLightingAreLeftAlone() throws InterruptedException {
        var device = new FakeDevice(null, rainbow());
        sut.update(device, true);
        Thread.sleep(150);
        assertEquals(List.of(), device.sent);
    }

    @Test
    void nothingIsDrawnWhileALightShowPlays() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        show.running = true;
        sut.update(device, true);
        Thread.sleep(150);
        assertEquals(List.of(), device.sent);
    }

    @Test
    void nothingIsDrawnWhileTheVisualizerShows() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.visualizing = serial -> true;
        sut.update(device, true);
        Thread.sleep(150);
        assertEquals(List.of(), device.sent);
        assertNull(device.painter());
    }

    @Test
    void theVisualizerStartingEndsTheDrawing() throws InterruptedException {
        var base = rainbow();
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        var visualizing = new java.util.concurrent.atomic.AtomicBoolean();
        sut.visualizing = serial -> visualizing.get();
        sut.update(device, true);
        waitFor(() -> !device.frames().isEmpty(), "drawing");

        visualizing.set(true);
        device.relight(); // the visualizer's first relight
        assertNull(device.painter(), "the drawing stepped aside");
        // Sent once, or twice when the drawing's own next step stepped aside just before the relight.
        assertFalse(device.sends().isEmpty(), "the lighting went to the panel, where the visualizer turns it per-control");
        assertTrue(device.sends().stream().allMatch(lc -> lc == base), "only the device's own lighting was sent");
        var frames = device.frames().size();
        Thread.sleep(150);
        assertEquals(frames, device.frames().size(), "the loop has stopped");
    }

    @Test
    void theDrawingStepsAsideForTheVisualizerEvenWithoutARelight() throws InterruptedException {
        var base = rainbow();
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        var visualizing = new java.util.concurrent.atomic.AtomicBoolean();
        sut.visualizing = serial -> visualizing.get();
        sut.update(device, true);
        waitFor(() -> !device.frames().isEmpty(), "drawing");

        visualizing.set(true);
        // Handing back clears the painter, then sends the lighting: wait for both.
        waitFor(() -> device.painter() == null && !device.sends().isEmpty(), "the next step hands back");
        assertEquals(List.of(base), device.sends());
    }

    @Test
    void lightingSentAgainByOthersKeepsTheDrawingGoing() throws InterruptedException {
        var base = rainbow();
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        sut.update(device, true);
        var other = new Thread(() -> { // a relight, a brightness change, a mute-colour refresh...
            var until = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < until) {
                device.setLighting(device.lightingConfig(), true);
                device.relight();
            }
        });
        other.start();
        other.join();
        assertEquals(List.of(), device.sends(), "every re-send is drawn instead");
        var frames = device.frames().size();
        waitFor(() -> device.frames().size() > frames + 2, "still drawing");
        sut.update(device, false);
        assertEquals(List.of(base), device.sends(), "and handed back once");
        assertSame(base, device.lightingConfig());
    }

    @Test
    void lightingChangedMeanwhileIsWhatIsDrawnAndHandedBack() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        var changed = LightingConfig.createAllColor("#654321");
        device.setLighting(changed, true); // e.g. a profile switch
        assertEquals("#654321", device.lastFrame().knobConfigs()[0].getColor1(), "drawn straight away");
        assertSame(changed, device.lightingConfig());
        assertEquals(List.of(), device.sends());
        sut.update(device, false);
        assertEquals(List.of(changed), device.sends());
    }

    @Test
    void switchingToCustomEndsIt() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        var custom = custom();
        device.setLighting(custom, true);
        assertEquals(List.of(custom), device.sends(), "per-control lighting goes to the panel: the overrides show in it");
        var frames = device.frames().size();
        Thread.sleep(150);
        sut.update(device, false);
        assertEquals(frames, device.frames().size(), "no more frames");
        assertEquals(List.of(custom), device.sends(), "nothing to hand back");
        assertNull(device.painter());
    }

    @Test
    void aLightShowInterruptsTheDrawingAndItResumesAfterwards() throws InterruptedException {
        var lightShow = new LightShow();
        sut.lightShow = lightShow;
        var base = LightingConfig.createAllColor("#123456");
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        sut.update(device, true);
        lightShow.play(device, (t, layout) -> com.getpcpanel.device.lightshow.SoftwareAnimation.frame(LightingConfig.createAllColor(SHOW_COLOR), layout, 0), 300);
        waitFor(() -> lightShow.isRunning(device.getSerialNumber()), "the show starts");
        device.relight(); // something re-sends the lighting during the show
        waitFor(() -> !lightShow.isRunning(device.getSerialNumber()), "the show ends");
        var frames = device.frames();
        var firstShow = indexOfColor(frames, SHOW_COLOR, 0);
        var lastShow = lastIndexOfColor(frames, SHOW_COLOR);
        assertTrue(firstShow > 0, "the show played");
        assertEquals(-1, indexOfColor(frames.subList(firstShow, lastShow + 1), "#123456", 0), "no notification frame during the show");
        assertTrue(indexOfColor(frames, "#123456", lastShow) > lastShow, "the notification frame is back after the show");
        assertEquals(List.of(), device.sends(), "the panel's own lighting was never sent meanwhile");
        sut.update(device, false);
        assertEquals(List.of(base), device.sends());
    }

    @Test
    void goingDarkDuringALightShowHandsBackAfterIt() throws InterruptedException {
        var lightShow = new LightShow();
        sut.lightShow = lightShow;
        var base = rainbow();
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        sut.update(device, true);
        lightShow.play(device, (t, layout) -> com.getpcpanel.device.lightshow.SoftwareAnimation.frame(LightingConfig.createAllColor(SHOW_COLOR), layout, 0), 300);
        waitFor(() -> lightShow.isRunning(device.getSerialNumber()), "the show starts");
        sut.update(device, false);
        assertEquals(List.of(), device.sends(), "not while the show plays");
        waitFor(() -> !device.sends().isEmpty(), "handed back after the show");
        assertEquals(List.of(base), device.sends());
        var frames = device.frames();
        assertTrue(frames.get(frames.size() - 1).knobConfigs()[0].getColor1().equalsIgnoreCase(SHOW_COLOR), "no notification frame after the show");
    }

    @Test
    void nothingIsDrawnWhileThePanelsAreDark() throws InterruptedException {
        var base = LightingConfig.createAllColor("#123456");
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, base);
        sut.update(device, true);
        sut.onPanelsDark(new PanelsDarkEvent(true));
        var frames = device.frames().size();
        device.relight();
        Thread.sleep(150);
        assertEquals(frames, device.frames().size(), "no frames while dark");

        sut.onPanelsDark(new PanelsDarkEvent(false));
        waitFor(() -> device.frames().size() > frames, "drawn again once lit, still lighting too");
    }

    @Test
    void lightingSetWhileDarkIsWhatIsDrawnAfterWake() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        sut.onPanelsDark(new PanelsDarkEvent(true));
        var changed = LightingConfig.createAllColor("#654321");
        device.setLighting(changed, true); // e.g. a profile switch while locked
        var frames = device.frames().size();
        sut.onPanelsDark(new PanelsDarkEvent(false));
        waitFor(() -> device.frames().size() > frames, "drawn again once lit");
        assertEquals("#654321", device.lastFrame().knobConfigs()[0].getColor1(), "the lighting set while dark, not the one before");
        sut.update(device, false);
        assertEquals(changed, device.sends().getLast(), "and that is what is handed back");
    }

    @Test
    void switchingToCustomWhileDarkEndsIt() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        sut.onPanelsDark(new PanelsDarkEvent(true));
        device.setLighting(custom(), true);
        assertNull(device.painter(), "per-control lighting shows the overrides itself");
        var frames = device.frames().size();
        sut.onPanelsDark(new PanelsDarkEvent(false));
        Thread.sleep(150);
        assertEquals(frames, device.frames().size(), "no frames after wake");
    }

    @Test
    void goingOffWhileDarkLeavesTheRelightToWake() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        sut.onPanelsDark(new PanelsDarkEvent(true));
        sut.update(device, false);
        var frames = device.frames().size();
        Thread.sleep(150);
        assertEquals(frames, device.frames().size());
        assertEquals(List.of(), device.sends(), "the panels stay dark");
        sut.onPanelsDark(new PanelsDarkEvent(false));
        sut.update(device, true);
        assertTrue(device.frames().size() > frames, "a new alert draws again once lit");
    }

    @Test
    void nothingIsDrawnWhileDarkEvenForANewAlert() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.onPanelsDark(new PanelsDarkEvent(true));
        sut.update(device, true);
        Thread.sleep(100);
        assertEquals(List.of(), device.sent);
    }

    @Test
    void shutdownStopsTheDrawingForGood() throws InterruptedException {
        var device = new FakeDevice(DeviceType.PCPANEL_PRO, rainbow());
        sut.update(device, true);
        sut.onShutdown(new ShutdownEvent());
        var count = device.sent.size();
        assertNull(device.painter());
        sut.update(device, true);
        Thread.sleep(150);
        assertEquals(count, device.sent.size(), "nothing after the shutdown lights-off");
    }

    @Test
    void onlyLightsADeviceHasCount() {
        var mini = new FakeDevice(DeviceType.PCPANEL_MINI, LightingConfig.createAllColor("#123456"));
        var pro = new FakeDevice(DeviceType.PCPANEL_PRO, LightingConfig.createAllColor("#123456"));
        assertTrue(AlertService.litOn(mini, Set.of("knob:3")));
        assertFalse(AlertService.litOn(mini, Set.of("knob:4", "slider:0", "logo")), "the Mini has four knobs and nothing else");
        assertTrue(AlertService.litOn(pro, Set.of("slider:3")));
        assertTrue(AlertService.litOn(pro, Set.of("logo")));
        assertFalse(AlertService.litOn(pro, Set.of()));
    }

    private static int indexOfColor(List<LightingConfig> frames, String color, int from) {
        for (var i = from; i < frames.size(); i++) {
            if (color.equalsIgnoreCase(frames.get(i).knobConfigs()[0].getColor1())) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOfColor(List<LightingConfig> frames, String color) {
        for (var i = frames.size() - 1; i >= 0; i--) {
            if (color.equalsIgnoreCase(frames.get(i).knobConfigs()[0].getColor1())) {
                return i;
            }
        }
        return -1;
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        var until = System.currentTimeMillis() + 2_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("Timed out: " + what);
            }
            Thread.sleep(5);
        }
    }

    private static final class FakeShow extends LightShow {
        volatile boolean running;

        @Override
        public boolean isRunning(String serial) {
            return running;
        }
    }

    /** What reached the "hardware": the device's lighting ({@code temporary} false) or a temporary frame. */
    private record Sent(LightingConfig config, boolean temporary) {
    }

    private static final class FakeDevice extends Device {
        private final List<Sent> sent = new CopyOnWriteArrayList<>();
        @Nullable private final DeviceType type;

        FakeDevice(@Nullable DeviceType type, LightingConfig lighting) {
            super(null, null, null, null, "fake", new DeviceSave(), DescriptorFactory.forType(type == null ? DeviceType.PCPANEL_PRO : type));
            this.type = type;
            setLighting(lighting, true);
            sent.clear();
        }

        List<LightingConfig> frames() {
            return sent.stream().filter(Sent::temporary).map(Sent::config).toList();
        }

        List<LightingConfig> sends() {
            return sent.stream().filter(s -> !s.temporary()).map(Sent::config).toList();
        }

        LightingConfig lastFrame() {
            var frames = frames();
            return frames.get(frames.size() - 1);
        }

        @Nullable
        @Override
        public DeviceType deviceType() {
            return type;
        }

        @Override
        protected void sendToDevice(LightingConfig config, boolean priority) {
            sent.add(new Sent(config, false));
        }

        @Override
        public void showTemporaryLighting(LightingConfig frame) {
            sent.add(new Sent(frame, true));
        }

        @Override
        public void setKnobRotation(int knob, int value) {
        }

        @Override
        public int getKnobRotation(int knob) {
            return 0;
        }

        @Override
        public void setButtonPressed(int button, boolean pressed) {
        }
    }
}
