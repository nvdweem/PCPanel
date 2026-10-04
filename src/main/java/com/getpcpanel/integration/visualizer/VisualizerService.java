package com.getpcpanel.integration.visualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.device.provider.pcpanel.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.device.provider.pcpanel.DeviceScanner;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.integration.visualizer.VisualizerPainter.Layout;
import com.getpcpanel.integration.visualizer.VisualizerPainter.Music;
import com.getpcpanel.integration.visualizer.VisualizerPainter.Paint;
import com.getpcpanel.integration.volume.platform.LoopbackCapture;
import com.getpcpanel.integration.volume.platform.PlaybackGate;
import com.getpcpanel.integration.volume.platform.PlaybackGate.Playing;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.profile.dto.VisualizerConfig;
import com.getpcpanel.profile.dto.VisualizerConfig.VisualizerWhen;
import com.getpcpanel.profile.dto.VisualizerSource;
import com.getpcpanel.rest.EventBroadcaster.LightingChangedEvent;
import com.getpcpanel.rest.EventBroadcaster.VisualColorsChangedEvent;
import com.getpcpanel.sleepdetection.PanelsDarkEvent;
import com.getpcpanel.sleepdetection.SleepDetector;
import com.getpcpanel.sleepdetection.SystemEvent;
import com.getpcpanel.profile.ProfileSwitchedEvent;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The music visualizer: panel lights that move with what the PC plays.
 *
 * <p>One thread, in one of three states:
 * <ul>
 *     <li><b>parked</b> while no connected device's active profile has the visualizer on, or the lights are off for a
 *     lock or sleep: it waits to be woken by a profile, lighting, device or system event (with a slow safety
 *     timeout), and nothing is captured;</li>
 *     <li><b>watching</b> a few times a second whether one of its sources has sound ({@link PlaybackGate}, no
 *     capture);</li>
 *     <li><b>capturing</b> the first source with sound ({@link LoopbackCapture}: an output by loopback, an input
 *     directly) at {@link #FRAME_MS}, analysing it ({@link BandAnalyzer}) and painting ({@link VisualizerPainter}), until
 *     it has been quiet for {@link #HOLD_MS}.</li>
 * </ul>
 *
 * <p>Each profile's {@link VisualizerConfig#getSources()} is an ordered list; it listens to the first that has sound
 * ({@link #pick}). With several devices showing it, the first device with a source that has sound decides.
 *
 * <p>The colours go out as colour overrides, below notification lights and above mute colours. A profile whose
 * lighting is a single colour or an animation is sent as per-control lighting while the visualizer shows (see
 * {@link #substitute}), since an animation can't be mixed with single lights; then every light is the visualizer's.
 */
@Log4j2
@Startup
@Priority(25)
@ApplicationScoped
public class VisualizerService implements IOverrideColorProviderProvider {
    static final long FRAME_MS = 50;
    /** How often it looks whether something plays while it isn't listening. */
    static final long WATCH_MS = 500;
    /** And while it is: the capture itself notices silence, so this only follows apps starting, stopping and moving. */
    static final long GATE_WHILE_CAPTURING_MS = 1_000;
    /** How long it keeps going through a gap in the music before it stops capturing. */
    static final long HOLD_MS = 3_000;
    static final long RETRY_MS = 5_000;
    /** How long it stays away from a source that was silent while it counted as having sound. */
    static final long QUIET_BACKOFF_MS = 30_000;
    /**
     * How long what it captures must be quiet before it moves to a source further down the list, or follows the
     * music to another output. A source higher up that gets sound takes over at once.
     */
    static final long SWITCH_AFTER_MS = 1_000;
    /** How long a moved control shows its position. */
    static final long POSITION_MS = 1_500;
    private static final long PARK_TIMEOUT_MS = 5_000;
    private static final long UI_REFRESH_MS = 100;
    /** A read that brings nothing within this long of the last one that did is a gap in delivery, not silence. */
    private static final long DATA_GAP_MS = 150;
    /** What is left of a beat flash one frame later. */
    private static final float GLOW_FALL = 0.7f;
    private static final int MAX_ANALOG = 16;

    @Inject DeviceHolder devices;
    @Inject LoopbackCapture capture;
    @Inject PlaybackGate gate;
    @Inject SleepDetector sleep;
    @Inject Event<VisualColorsChangedEvent> visualColorsChanged;

    LongSupplier clock = System::currentTimeMillis;
    private final ColorOverrideHolder holder = new ColorOverrideHolder();
    private final Object wakeLock = new Object();
    private boolean wakeRequested;
    private volatile boolean running;
    private volatile boolean shutDown;
    private final Object stepping = new Object();
    @Nullable private Thread thread;

    // Only touched by the visualizer thread (or a test calling step()).
    private boolean capturing;
    private long startFailedAt = -RETRY_MS;
    private long heardAt;
    /** When the capture last brought samples, and what they showed. */
    private long dataAt;
    private Music lastMusic = Music.QUIET;
    @Nullable private Target capturingTarget;
    private int capturingIndex = Integer.MAX_VALUE;
    @Nullable private Pick preferred;
    @Nullable private Target quietTarget;
    private long quietUntil = Long.MIN_VALUE;
    private long gateAt = Long.MIN_VALUE / 2;
    private long gateTrueAt = Long.MIN_VALUE / 2;
    @Nullable private BandAnalyzer analyzer;
    private float[] samples = new float[0];
    private float glow;
    private final Map<String, Long> playingAt = new HashMap<>();
    private final Map<String, Paint> painted = new HashMap<>();
    private final Map<String, Long> uiRefreshedAt = new HashMap<>();
    /** The source it captures now, for the UI. */
    @Nullable private volatile VisualizerSource listening;
    private final long startedAt = System.currentTimeMillis();

    /** Devices the visualizer currently drives, with their layout: read by {@link #substitute} on other threads. */
    private final Map<String, Layout> showing = new ConcurrentHashMap<>();
    /** Recent control moves: per device, per analog control, the position 0..1 and when. */
    private final Map<String, float[]> positions = new ConcurrentHashMap<>();
    private final Map<String, long[]> movedAt = new ConcurrentHashMap<>();

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::run, "visualizer");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return holder;
    }

    /** Whether it is showing on {@code serial} right now. */
    public boolean isShowing(String serial) {
        return showing.containsKey(serial);
    }

    /** The source it is listening to right now; null while it isn't capturing. */
    public @Nullable VisualizerSource listeningTo() {
        return listening;
    }

    /** Whether this platform can run it, and if not, why (for the UI). */
    public @Nullable String unavailableReason() {
        return capture.supported() ? null : capture.unavailableReason();
    }

    /**
     * The lighting to send for a device: unchanged, except that while the visualizer shows on a device whose profile
     * lighting is not per control, a per-control config the visualizer's overrides fill in.
     */
    public LightingConfig substitute(String serial, LightingConfig config) {
        var layout = showing.get(serial);
        if (layout == null || config == null || config.lightingMode() == LightingMode.CUSTOM) {
            return config;
        }
        var custom = new LightingConfig(layout.knobs(), layout.sliders());
        custom.setLightingMode(LightingMode.CUSTOM);
        custom.setGlobalBrightness(config.getGlobalBrightness());
        return custom;
    }

    // ── wake-ups ───────────────────────────────────────────────────────────────

    void onLighting(@Observes LightingChangedEvent e) {
        wake();
    }

    void onProfile(@Observes ProfileSwitchedEvent e) {
        wake();
    }

    void onConnected(@Observes DeviceScanner.DeviceConnectedEvent e) {
        wake();
    }

    void onDisconnected(@Observes DeviceScanner.DeviceDisconnectedEvent e) {
        wake();
    }

    void onSave(@Observes SaveService.SaveEvent e) {
        wake();
    }

    void onSystem(@Observes SystemEvent e) {
        wake();
    }

    void onPanelsDark(@Observes PanelsDarkEvent e) {
        wake();
    }

    /** Before the lights-off at shutdown ({@code SleepDetector}): lets a step in progress finish, then sends nothing more. */
    void onShutdown(@Observes @Priority(1) ShutdownEvent e) {
        synchronized (stepping) {
            shutDown = true;
        }
        stop();
    }

    void onKnob(@Observes KnobRotateEvent e) {
        if (e.initial() || e.knob() < 0 || e.knob() >= MAX_ANALOG || !showing.containsKey(e.serialNum())) {
            return;
        }
        positions.computeIfAbsent(e.serialNum(), k -> nan(MAX_ANALOG))[e.knob()] = Math.clamp(e.value() / 255f, 0f, 1f);
        movedAt.computeIfAbsent(e.serialNum(), k -> new long[MAX_ANALOG])[e.knob()] = clock.getAsLong();
    }

    private void wake() {
        synchronized (wakeLock) {
            wakeRequested = true;
            wakeLock.notifyAll();
        }
    }

    // ── the loop ───────────────────────────────────────────────────────────────

    private void run() {
        while (running) {
            long next;
            try {
                synchronized (stepping) {
                    if (shutDown) {
                        stopCapture();
                        return;
                    }
                    next = step();
                }
            } catch (Throwable t) {
                log.warn("The visualizer failed to update", t);
                next = WATCH_MS;
            }
            try {
                if (next < 0) {
                    synchronized (wakeLock) {
                        if (!wakeRequested) {
                            wakeLock.wait(PARK_TIMEOUT_MS);
                        }
                        wakeRequested = false;
                    }
                } else {
                    Thread.sleep(next);
                }
            } catch (InterruptedException e) {
                stopCapture();
                return;
            }
        }
    }

    /** A device that wants the visualizer, with its settings. */
    private record Want(Device device, VisualizerConfig config, Layout layout, boolean allLights) {
    }

    /** One update. Returns how long to wait before the next, or -1 to park until woken. */
    long step() {
        var now = clock.getAsLong();
        var wants = wanted();
        if (wants.isEmpty() || sleep.isDark() || !capture.supported()) {
            stopCapture();
            clearExcept(Set.of());
            return -1;
        }

        if (now - gateAt >= (capturing ? GATE_WHILE_CAPTURING_MS : WATCH_MS)) {
            gateAt = now;
            var playing = gate.check();
            preferred = null;
            for (var want : wants) {
                var sounding = pick(want.config(), playing, t -> false);
                if (sounding == null) {
                    continue;
                }
                playingAt.put(want.device().getSerialNumber(), now);
                gateTrueAt = now;
                // A source that stayed silent while it counted as having sound is left alone for a while: the next.
                var pick = backedOff(sounding.target(), now) ? pick(want.config(), playing, t -> backedOff(t, now)) : sounding;
                if (preferred == null) {
                    preferred = pick;
                }
            }
            if (gateTrueAt != now) {
                quietUntil = Long.MIN_VALUE; // nothing has sound any more, so a new start is worth listening to again
            }
            // A source higher up got sound: switch now. Further down, or the music moved to another output (another
            // app, or Wave Link routing): once this one is quiet.
            if (capturing && preferred != null && !preferred.target().equals(capturingTarget)
                    && (preferred.index() < capturingIndex || now - heardAt >= SWITCH_AFTER_MS)) {
                stopCapture();
            }
        }

        if (!capturing && preferred != null && !backedOff(preferred.target(), now) && now - gateTrueAt <= HOLD_MS && now - startFailedAt >= RETRY_MS) {
            startCapture(now, preferred);
        } else if (capturing && now - gateTrueAt > HOLD_MS) {
            stopCapture();
        } else if (capturing && now - heardAt > HOLD_MS) {
            // It counts as having sound, but what it captures stays silent: don't keep reopening it.
            quietTarget = capturingTarget;
            quietUntil = now + QUIET_BACKOFF_MS;
            log.debug("{} counts as having sound but stays silent; not listening there for {} s", describe(capturingTarget), QUIET_BACKOFF_MS / 1000);
            stopCapture();
        }

        var music = Music.QUIET;
        if (capturing) {
            music = listen(now);
        } else {
            glow = 0;
        }

        var shown = new ArrayList<String>();
        var animating = false;
        for (var want : wants) {
            var serial = want.device().getSerialNumber();
            var show = want.config().getWhen() == VisualizerWhen.ALWAYS
                    || capturing && now - playingAt.getOrDefault(serial, Long.MIN_VALUE / 2) <= HOLD_MS;
            if (!show) {
                continue;
            }
            shown.add(serial);
            var moves = activePositions(serial, now);
            animating |= moves != null;
            paint(want, VisualizerPainter.paint(want.config(), want.layout(), music, moves == null ? nan(0) : moves, (now - startedAt) / 1000.0, want.allLights()), now);
        }
        clearExcept(Set.copyOf(shown));
        return capturing || animating ? FRAME_MS : WATCH_MS;
    }

    private List<Want> wanted() {
        var result = new ArrayList<Want>();
        for (var device : devices.all()) {
            var type = device.deviceType();
            LightingConfig lc;
            try {
                lc = device.lightingConfig();
            } catch (Exception e) {
                continue;
            }
            if (type == null || lc == null || lc.getVisualizer() == null || !lc.getVisualizer().enabled()) {
                continue;
            }
            result.add(new Want(device, lc.getVisualizer(), layout(type), lc.lightingMode() != LightingMode.CUSTOM));
        }
        return result;
    }

    static Layout layout(DeviceType type) {
        return switch (type) {
            case PCPANEL_PRO -> new Layout(5, 4, true, true);
            case PCPANEL_MINI, PCPANEL_RGB -> new Layout(4, 0, false, false);
        };
    }

    /** Where it captures: an output (by loopback) or an input; {@code device} null is the default one. */
    record Target(boolean input, @Nullable String device) {
    }

    /** The source a config listens to, its place in the list, and where it is captured. */
    record Pick(int index, VisualizerSource source, Target target) {
    }

    private boolean backedOff(Target target, long now) {
        return now < quietUntil && target.equals(quietTarget);
    }

    /** The first of the config's sources that has sound and {@code avoid} doesn't refuse; null when there is none. */
    @Nullable
    static Pick pick(VisualizerConfig config, Playing playing, Predicate<Target> avoid) {
        var sources = config.getSources();
        for (var i = 0; i < sources.length; i++) {
            var target = sources[i] == null || sources[i].kind() == null ? null : sounding(sources[i], playing);
            if (target != null && !avoid.test(target)) {
                return new Pick(i, sources[i], target);
            }
        }
        return null;
    }

    /** Where to capture {@code source} when it has sound; null when it hasn't. */
    @Nullable
    static Target sounding(VisualizerSource source, Playing playing) {
        return switch (source.kind()) {
            case OUTPUT -> playing.output(source.device()) ? new Target(false, source.device()) : null;
            case INPUT -> playing.input(source.device()) ? new Target(true, source.device()) : null;
            case APP -> source.app() == null || source.app().isBlank() ? null : appTarget(playing, List.of(source.app()));
            case ANY_APP -> appTarget(playing, List.of());
        };
    }

    /** The output where these apps play loudest (the default output when unknown), while they play. */
    @Nullable
    private static Target appTarget(Playing playing, List<String> apps) {
        return playing.any(apps) ? new Target(false, playing.device(apps)) : null;
    }

    private static String describe(@Nullable Target target) {
        if (target == null) {
            return "nothing";
        }
        if (target.device() == null) {
            return target.input() ? "the default input" : "the default output";
        }
        return target.device();
    }

    private void startCapture(long now, Pick pick) {
        var target = pick.target();
        if (!capture.start(target.device(), target.input())) {
            if (startFailedAt < 0 || now - startFailedAt > 60_000) {
                log.info("The visualizer could not start listening to {}; trying again every {} s", describe(target), RETRY_MS / 1000);
            }
            startFailedAt = now;
            return;
        }
        capturingTarget = target;
        capturingIndex = pick.index();
        listening = pick.source();
        log.debug("Visualizer listening to {} ({}) at {} Hz", describe(target), pick.source(), capture.sampleRate());
        analyzer = new BandAnalyzer(capture.sampleRate());
        if (samples.length < capture.sampleRate()) {
            samples = new float[capture.sampleRate()]; // a second's worth, more than a frame ever brings
        }
        capturing = true;
        heardAt = now;
    }

    private void stopCapture() {
        if (capturing) {
            capture.stop();
            capturing = false;
            capturingIndex = Integer.MAX_VALUE;
            listening = null;
            analyzer = null;
            log.debug("Visualizer stopped listening");
        }
    }

    private Music listen(long now) {
        var n = capture.read(samples);
        if (n < 0) {
            // The output changed or went away: open the new one on the next step.
            stopCapture();
            startFailedAt = now - RETRY_MS;
            return Music.QUIET;
        }
        if (n == 0 && now - dataAt < DATA_GAP_MS) {
            // Nothing new yet (parec hands over its buffer in chunks): hold the last frame rather than read it as silence.
            glow *= GLOW_FALL;
            return new Music(lastMusic.bands(), lastMusic.overall(), glow);
        }
        if (n > 0) {
            dataAt = now;
        }
        var frame = analyzer.analyze(samples, n);
        if (!frame.silent()) {
            heardAt = now;
        }
        glow = frame.beat() ? 1 : glow * GLOW_FALL;
        var bands = new float[BandAnalyzer.BANDS];
        for (var b = 0; b < bands.length; b++) {
            bands[b] = frame.band(b);
        }
        lastMusic = new Music(bands, frame.overall(), glow);
        return lastMusic;
    }

    @Nullable
    private float[] activePositions(String serial, long now) {
        var times = movedAt.get(serial);
        var values = positions.get(serial);
        if (times == null || values == null) {
            return null;
        }
        float[] out = null;
        for (var i = 0; i < times.length; i++) {
            if (times[i] != 0 && now - times[i] <= POSITION_MS) {
                if (out == null) {
                    out = nan(times.length);
                }
                out[i] = values[i];
            }
        }
        return out;
    }

    // ── output ─────────────────────────────────────────────────────────────────

    private void paint(Want want, Paint paint, long now) {
        var serial = want.device().getSerialNumber();
        var first = showing.put(serial, want.layout()) == null;
        if (!first && paint.equals(painted.get(serial))) {
            return; // nothing visibly changed: leave the panel alone
        }
        painted.put(serial, paint);
        for (var i = 0; i < paint.knobs().length; i++) {
            var c = paint.knobs()[i];
            holder.setDialOverride(serial, i, c == null ? null : new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(c));
        }
        for (var i = 0; i < paint.sliderBottoms().length; i++) {
            var bottom = paint.sliderBottoms()[i];
            holder.setSliderOverride(serial, i,
                    bottom == null ? null : new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC_GRADIENT).setColor1(bottom).setColor2(paint.sliderTops()[i]));
        }
        for (var i = 0; i < paint.labels().length; i++) {
            var c = paint.labels()[i];
            holder.setSliderLabelOverride(serial, i, c == null ? null : new SingleSliderLabelLightingConfig().setMode(SINGLE_SLIDER_LABEL_MODE.STATIC).setColor(c));
        }
        if (want.layout().logo()) {
            holder.setLogoOverride(serial, paint.logo() == null ? null : new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(paint.logo()));
        }
        relight(want.device(), now, first);
    }

    /** Hands every device not in {@code keep} back to its own lighting. */
    private void clearExcept(Set<String> keep) {
        for (var serial : List.copyOf(showing.keySet())) {
            if (keep.contains(serial)) {
                continue;
            }
            var layout = showing.remove(serial);
            painted.remove(serial);
            positions.remove(serial);
            movedAt.remove(serial);
            for (var i = 0; i < layout.knobs(); i++) {
                holder.setDialOverride(serial, i, null);
            }
            for (var i = 0; i < layout.sliders(); i++) {
                holder.setSliderOverride(serial, i, null);
                holder.setSliderLabelOverride(serial, i, null);
            }
            holder.setLogoOverride(serial, null);
            devices.getDevice(serial).ifPresent(d -> relight(d, clock.getAsLong(), true));
        }
    }

    private void relight(Device device, long now, boolean forceUi) {
        var serial = device.getSerialNumber();
        if (sleep.isDark()) {
            return;
        }
        try {
            device.setLighting(device.lightingConfig(), true);
        } catch (Exception e) {
            log.debug("Unable to send visualizer lighting to {}", serial, e);
            return;
        }
        // The panel follows every frame; the on-screen device a few times a second.
        if (forceUi || now - uiRefreshedAt.getOrDefault(serial, 0L) >= UI_REFRESH_MS) {
            uiRefreshedAt.put(serial, now);
            visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
        }
    }

    private static float[] nan(int n) {
        var out = new float[n];
        Arrays.fill(out, Float.NaN);
        return out;
    }
}
