package com.getpcpanel.integration.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.integration.volume.platform.LoopbackCapture;
import com.getpcpanel.integration.volume.platform.PlaybackGate;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.VisualizerConfig;
import com.getpcpanel.profile.dto.VisualizerConfig.VisualizerWhen;
import com.getpcpanel.profile.dto.VisualizerSource;
import com.getpcpanel.rest.EventBroadcaster.VisualColorsChangedEvent;
import com.getpcpanel.sleepdetection.SleepDetector;

import jakarta.enterprise.event.Event;

class VisualizerServiceTest {
    private static final String SERIAL = "pro-1";

    private VisualizerService service;
    private FakeCapture capture;
    private FakeGate gate;
    private Device device;
    private LightingConfig lighting;
    private SleepDetector sleep;
    private Save save;
    private long now;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        now = 1_000_000;
        capture = new FakeCapture();
        gate = new FakeGate();
        lighting = new LightingConfig(5, 4);
        lighting.setLightingMode(LightingMode.CUSTOM);
        device = mock(Device.class);
        when(device.getSerialNumber()).thenReturn(SERIAL);
        when(device.deviceType()).thenReturn(DeviceType.PCPANEL_PRO);
        when(device.lightingConfig()).thenAnswer(i -> lighting);
        var devices = mock(DeviceHolder.class);
        when(devices.all()).thenReturn(List.of(device));
        when(devices.getDevice(SERIAL)).thenReturn(Optional.of(device));
        sleep = mock(SleepDetector.class);

        service = new VisualizerService();
        service.devices = devices;
        service.capture = capture;
        service.gate = gate;
        service.sleep = sleep;
        save = new Save();
        service.save = mock(SaveService.class);
        when(service.save.get()).thenReturn(save);
        service.visualColorsChanged = mock(Event.class);
        service.clock = () -> now;
    }

    /** Listening to these apps, or to whatever plays loudest without any. */
    private void visualizer(VisualizerWhen when, String... apps) {
        sources(when, apps.length == 0 ? new VisualizerSource[] { VisualizerSource.anyApp() } : Arrays.stream(apps).map(VisualizerSource::app).toArray(VisualizerSource[]::new));
    }

    private void sources(VisualizerWhen when, VisualizerSource... sources) {
        var cfg = new VisualizerConfig();
        cfg.setWhen(when);
        cfg.setSources(sources);
        lighting.setVisualizer(cfg);
    }

    private long step(long advanceMs) {
        now += advanceMs;
        return service.step();
    }

    private boolean knobDriven() {
        return service.getOverrideColorProvider().getDialOverride(SERIAL, 0).isPresent();
    }

    @Test
    void parksWhileNoProfileHasItOn() {
        assertEquals(-1, step(0));
        visualizer(VisualizerWhen.OFF);
        assertEquals(-1, step(0));
        assertEquals(0, capture.starts);
    }

    @Test
    void watchesWithoutCapturingWhileNothingPlays() {
        visualizer(VisualizerWhen.PLAYING);
        assertEquals(VisualizerService.WATCH_MS, step(0));
        assertEquals(VisualizerService.WATCH_MS, step(VisualizerService.WATCH_MS));
        assertEquals(0, capture.starts);
        assertFalse(knobDriven());
    }

    @Test
    void capturesAndPaintsWhileSomethingPlays() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        assertEquals(VisualizerService.FRAME_MS, step(0));
        assertEquals(1, capture.starts);
        assertTrue(knobDriven());
        verify(device).setLighting(any(), anyBoolean());
    }

    @Test
    void stopsCapturingAfterAQuietGap() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        step(0);
        capture.loud = false; // the player still holds its stream, but it's silent
        for (var t = 0; t < VisualizerService.HOLD_MS; t += 50) {
            step(50);
        }
        assertTrue(capture.open, "keeps listening through a short gap");
        step(100);
        assertFalse(capture.open);
        assertFalse(knobDriven(), "the lights go back to the profile's own");
    }

    @Test
    void listensWhereTheMusicPlays() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("music.exe");
        gate.device = "wave-link-music";
        capture.loud = true;
        step(0);
        assertEquals("wave-link-music", capture.device, "not the default output: the app plays on another one");

        capture.loud = false;
        gate.device = "speakers";
        step(VisualizerService.WATCH_MS);
        assertEquals("wave-link-music", capture.device, "a moment of quiet isn't a move");
        for (var t = 0; t < VisualizerService.SWITCH_AFTER_MS; t += 250) {
            step(VisualizerService.WATCH_MS);
        }
        step(50);
        assertEquals("speakers", capture.device, "it follows once the old output stays quiet");
    }

    @Test
    void anOutputThatStaysSilentIsNotReopenedOverAndOver() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("wavelink.exe"); // counts as playing, but nothing reaches the output
        capture.loud = false;
        for (var t = 0; t < 20_000; t += 50) {
            step(50);
        }
        assertEquals(1, capture.starts, "it backs off instead of restarting every few seconds");
        gate.playing = List.of();
        step(VisualizerService.WATCH_MS);
        gate.playing = List.of("wavelink.exe");
        step(VisualizerService.WATCH_MS);
        assertEquals(2, capture.starts, "a new start of playback is worth listening to again");
    }

    @Test
    void aReadThatBringsNothingYetIsNotSilence() {
        var cfg = new VisualizerConfig();
        cfg.setWhen(VisualizerWhen.PLAYING);
        cfg.setStyle(VisualizerConfig.VisualizerStyle.PULSE);
        lighting.setVisualizer(cfg);
        gate.playing = List.of("music.exe");
        capture.loud = true;
        capture.chunky = true;
        step(0);
        var colors = new ArrayList<String>();
        for (var i = 0; i < 6; i++) {
            step(50);
            colors.add(service.getOverrideColorProvider().getLogoOverride(SERIAL).orElseThrow().getColor());
        }
        // Reads alternate: data at step(0), nothing, data, nothing, ... so steps 2 and 4 repeat steps 1 and 3.
        for (var i = 1; i + 1 < colors.size(); i += 2) {
            assertEquals(colors.get(i), colors.get(i + 1), "an empty read holds the last frame: " + colors);
        }
    }

    @Test
    void sendsNothingAfterTheShutdownEvent() throws InterruptedException {
        visualizer(VisualizerWhen.ALWAYS); // rainbow hues drift, so every step relights
        service.clock = System::currentTimeMillis;
        service.start();
        try {
            verify(device, timeout(2_000).atLeast(2)).setLighting(any(), anyBoolean());
            service.onShutdown(null);
            clearInvocations(device);
            Thread.sleep(3 * VisualizerService.WATCH_MS);
            verify(device, never()).setLighting(any(), anyBoolean());
        } finally {
            service.stop();
        }
    }

    @Test
    void onlyTheChosenAppsCount() {
        visualizer(VisualizerWhen.PLAYING, "spotify.exe");
        gate.playing = List.of("chrome.exe");
        step(0);
        assertEquals(0, capture.starts);
        gate.playing = List.of("chrome.exe", "spotify.exe");
        step(VisualizerService.WATCH_MS);
        assertEquals(1, capture.starts);
    }

    @Test
    void allTheTimeShowsEvenInSilence() {
        visualizer(VisualizerWhen.ALWAYS);
        assertEquals(VisualizerService.WATCH_MS, step(0));
        assertEquals(0, capture.starts);
        assertTrue(knobDriven());
    }

    @Test
    void anUnchangedFrameLeavesThePanelAlone() {
        visualizer(VisualizerWhen.ALWAYS);
        lighting.getVisualizer().setStyle(VisualizerConfig.VisualizerStyle.TWO_COLORS); // the rainbow's hues drift, so it does change
        step(0);
        clearInvocations(device);
        step(VisualizerService.WATCH_MS);
        verify(device, never()).setLighting(any(), anyBoolean());
    }

    @Test
    void parksAndLetsGoWhileTheLightsAreOffForALock() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        step(0);
        when(sleep.isDark()).thenReturn(true);
        assertEquals(-1, step(50));
        assertFalse(capture.open);
        assertFalse(knobDriven());
    }

    private void lockedButAwake() {
        when(sleep.isDark()).thenReturn(true);
        when(sleep.isDarkButAwake()).thenReturn(true);
    }

    @Test
    void withTheToggleOnItKeepsShowingWhileLockedOnTheDarkPanel() {
        save.setVisualizerWhileLocked(true);
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        lockedButAwake();
        assertEquals(VisualizerService.FRAME_MS, step(0));
        assertTrue(capture.open);
        assertTrue(knobDriven());
        assertTrue(service.isShowing(SERIAL));
        verify(sleep, atLeastOnce()).showDarkFrame(SERIAL);
        verify(device, never()).setLighting(any(), anyBoolean());
    }

    @Test
    void withTheToggleOnItStillParksWhileAsleep() {
        save.setVisualizerWhileLocked(true);
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        step(0);
        when(sleep.isDark()).thenReturn(true);
        assertEquals(-1, step(50));
        assertFalse(capture.open);
        assertFalse(knobDriven());
        verify(sleep, never()).showDarkFrame(anyString());
    }

    @Test
    void stoppingWhileLockedHandsTheDarkPanelBack() {
        save.setVisualizerWhileLocked(true);
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        lockedButAwake();
        step(0);
        clearInvocations(sleep);
        gate.playing = List.of();
        capture.loud = false;
        for (var t = 0; t <= VisualizerService.HOLD_MS + VisualizerService.WATCH_MS; t += 50) {
            step(50);
        }
        assertFalse(knobDriven());
        assertFalse(service.isShowing(SERIAL));
        verify(sleep, atLeastOnce()).showDarkFrame(SERIAL); // with nothing left to show, the panel goes off
        verify(device, never()).setLighting(any(), anyBoolean());
    }

    @Test
    void afterTheWakeItSendsItsLightingAgainEvenWhenNothingChanged() {
        save.setVisualizerWhileLocked(true);
        visualizer(VisualizerWhen.ALWAYS);
        lighting.getVisualizer().setStyle(VisualizerConfig.VisualizerStyle.TWO_COLORS);
        lockedButAwake();
        step(0);
        when(sleep.isDark()).thenReturn(false);
        when(sleep.isDarkButAwake()).thenReturn(false);
        service.onPanelsDark(new com.getpcpanel.sleepdetection.PanelsDarkEvent(false, true));
        step(VisualizerService.WATCH_MS);
        verify(device).setLighting(any(), anyBoolean()); // the wake relight sent the profile's lighting without it
    }

    @Test
    void itShowsOnDarkPanelsOnlyWithTheToggleOn() {
        assertFalse(service.showsWhileDark());
        save.setVisualizerWhileLocked(true);
        assertTrue(service.showsWhileDark());
    }

    @Test
    void reopensWhenTheOutputChanges() {
        visualizer(VisualizerWhen.PLAYING);
        gate.playing = List.of("spotify.exe");
        capture.loud = true;
        step(0);
        capture.broken = true;
        step(50);
        capture.broken = false;
        step(50);
        assertEquals(2, capture.starts);
        assertTrue(capture.open);
    }

    @Test
    void anAnimatedProfileIsSentAsPerControlLightingWhileShowing() {
        lighting.setLightingMode(LightingMode.ALL_RAINBOW);
        lighting.setGlobalBrightness(40);
        assertSame(lighting, service.substitute(SERIAL, lighting), "nothing changes while it doesn't show");
        visualizer(VisualizerWhen.ALWAYS);
        step(0);
        var sent = service.substitute(SERIAL, lighting);
        assertEquals(LightingMode.CUSTOM, sent.lightingMode());
        assertEquals(40, sent.getGlobalBrightness());
        assertTrue(service.getOverrideColorProvider().getLogoOverride(SERIAL).isPresent(), "every light is the visualizer's");

        lighting.setLightingMode(LightingMode.CUSTOM);
        assertSame(lighting, service.substitute(SERIAL, lighting), "per-control lighting is used as it is");
    }

    @Test
    void aMovedControlIsShownForAMoment() {
        var cfg = new VisualizerConfig();
        cfg.setWhen(VisualizerWhen.ALWAYS);
        cfg.setStyle(VisualizerConfig.VisualizerStyle.TWO_COLORS);
        cfg.setLowColor("#000000");
        cfg.setHighColor("#FFFFFF");
        lighting.setVisualizer(cfg);
        step(0);
        var quiet = service.getOverrideColorProvider().getDialOverride(SERIAL, 0).orElseThrow().getColor1();
        service.onKnob(new KnobRotateEvent(SERIAL, 0, 255, false));
        assertEquals(VisualizerService.FRAME_MS, step(50), "it animates while a position shows");
        assertEquals("#FFFFFF", service.getOverrideColorProvider().getDialOverride(SERIAL, 0).orElseThrow().getColor1());
        step(VisualizerService.POSITION_MS + 1);
        assertEquals(quiet, service.getOverrideColorProvider().getDialOverride(SERIAL, 0).orElseThrow().getColor1());
    }

    @Test
    void itListensToTheFirstSourceThatHasSound() {
        sources(VisualizerWhen.PLAYING, VisualizerSource.output("speakers"), VisualizerSource.input(null));
        capture.loud = true;
        gate.sounding.add("in:");
        step(0);
        assertTrue(capture.open);
        assertTrue(capture.input, "only the microphone has sound");
        assertEquals(null, capture.device);
        assertEquals(VisualizerSource.input(null), service.listeningTo());

        gate.sounding.add("out:speakers");
        step(VisualizerService.GATE_WHILE_CAPTURING_MS);
        step(50);
        assertFalse(capture.input, "a source higher up takes over at once");
        assertEquals("speakers", capture.device);
        assertEquals(VisualizerSource.output("speakers"), service.listeningTo());
    }

    @Test
    void itFallsBackDownTheListOnceTheFirstIsQuiet() {
        sources(VisualizerWhen.PLAYING, VisualizerSource.output(null), VisualizerSource.input("mic"));
        capture.loud = true;
        gate.sounding.add("out:");
        gate.sounding.add("in:mic");
        step(0);
        assertFalse(capture.input);

        gate.sounding.remove("out:");
        capture.loud = false;
        for (var t = 0; t < VisualizerService.SWITCH_AFTER_MS - 100; t += 50) {
            step(50);
        }
        assertFalse(capture.input, "a moment of quiet isn't a reason to move");
        for (var t = 0; t <= VisualizerService.GATE_WHILE_CAPTURING_MS + 100; t += 50) {
            step(50);
        }
        assertTrue(capture.input);
        assertEquals("mic", capture.device);
    }

    @Test
    void whilePlayingItShowsOnlyWhileASourceHasSound() {
        sources(VisualizerWhen.PLAYING, VisualizerSource.output("speakers"));
        gate.playing = List.of("spotify.exe"); // an app, but not on the output it listens to
        step(0);
        assertEquals(0, capture.starts);
        assertFalse(knobDriven());
    }

    @Test
    void alwaysWithSilenceShowsDarkWithoutListening() {
        sources(VisualizerWhen.ALWAYS, VisualizerSource.output(null), VisualizerSource.input(null));
        step(0);
        assertEquals(0, capture.starts);
        assertTrue(knobDriven());
        assertNull(service.listeningTo());
    }

    @Test
    void anAppSourceListensWhereThatAppPlays() {
        sources(VisualizerWhen.PLAYING, VisualizerSource.app("spotify.exe"), VisualizerSource.output(null));
        gate.playing = List.of("spotify.exe");
        gate.device = "wave-link-music";
        capture.loud = true;
        step(0);
        assertEquals("wave-link-music", capture.device);
        assertFalse(capture.input);
    }

    @Test
    void theSourcePickSkipsWhatIsAvoided() {
        var cfg = new VisualizerConfig();
        cfg.setSources(new VisualizerSource[] { VisualizerSource.anyApp(), VisualizerSource.output("b") });
        gate.playing = List.of("x.exe");
        gate.device = "a";
        gate.sounding.add("out:b");
        var playing = gate.check();
        assertEquals(new VisualizerService.Target(false, "a"), VisualizerService.pick(cfg, playing, t -> false).target());
        var next = VisualizerService.pick(cfg, playing, t -> "a".equals(t.device()));
        assertEquals(1, next.index());
        assertEquals(new VisualizerService.Target(false, "b"), next.target());
        assertNull(VisualizerService.pick(cfg, playing, t -> true));
    }

    /** A capture that plays noise when {@link #loud}, else silence. */
    private static final class FakeCapture implements LoopbackCapture {
        boolean open;
        boolean input;
        boolean loud;
        boolean broken;
        int starts;
        String device;
        /** Delivers on every other read only, as parec does with a buffer larger than a frame. */
        boolean chunky;
        private int reads;
        private final Random random = new Random(7);

        @Override
        public boolean supported() {
            return true;
        }

        @Override
        public String unavailableReason() {
            return null;
        }

        @Override
        public boolean start(String deviceId, boolean input) {
            starts++;
            open = true;
            device = deviceId;
            this.input = input;
            return true;
        }

        @Override
        public void stop() {
            open = false;
        }

        @Override
        public int sampleRate() {
            return 22_050;
        }

        @Override
        public int read(float[] into) {
            if (broken) {
                return -1;
            }
            if (chunky && reads++ % 2 == 1) {
                return 0;
            }
            var n = Math.min(into.length, 1102);
            for (var i = 0; i < n; i++) {
                into[i] = loud ? (float) (random.nextDouble() - 0.5) : 0f;
            }
            return n;
        }
    }

    /** Plays whatever {@link #playing} names, on {@link #device}; the outputs and inputs in {@link #sounding} have sound ("" the default). */
    private static final class FakeGate implements PlaybackGate {
        List<String> playing = new ArrayList<>();
        String device;
        Set<String> sounding = new HashSet<>();

        @Override
        public Playing check() {
            var now = List.copyOf(playing);
            var on = device;
            var heard = Set.copyOf(sounding);
            return new Playing() {
                @Override
                public boolean output(String deviceId) {
                    return heard.contains("out:" + (deviceId == null ? "" : deviceId));
                }

                @Override
                public boolean input(String deviceId) {
                    return heard.contains("in:" + (deviceId == null ? "" : deviceId));
                }

                @Override
                public boolean any(Collection<String> apps) {
                    return !now.isEmpty() && (apps.isEmpty() || apps.stream().anyMatch(now::contains));
                }

                @Override
                public String device(Collection<String> apps) {
                    return any(apps) ? on : null;
                }
            };
        }
    }
}
