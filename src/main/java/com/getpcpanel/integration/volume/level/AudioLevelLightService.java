package com.getpcpanel.integration.volume.level;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.curve.CurveService;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.integration.volume.command.CommandVolumeDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeFocus;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.integration.volume.platform.AudioLevelMeter;
import com.getpcpanel.integration.volume.platform.AudioSession;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.LightingConfig.LightingMode;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig;
import com.getpcpanel.profile.dto.SingleLogoLightingConfig.SINGLE_LOGO_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig.SINGLE_SLIDER_MODE;
import com.getpcpanel.rest.EventBroadcaster.VisualColorsChangedEvent;
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * The "Audio level" light mode: a knob, slider or logo light that follows how loud something is playing, blending
 * from its quiet colour to its loud colour. What it meters is the light's {@code audioLevelSource}: an audio-device
 * name, {@code app:<exe>}, or blank to follow what the control's dial actions drive (the apps of an App-volume
 * action, the device of a Device-volume action, the focused app of a focus dial), else the default output.
 *
 * <p>One thread samples the {@link AudioLevelMeter} ({@link #INTERVAL_MS}) only while a connected device shows such a
 * light, and feeds the result in as a colour override, below the mute colours. Without a meter the light shows its
 * loud colour (see {@code OutputInterpreter}).
 */
@Log4j2
@Startup
@Priority(-200)
@ApplicationScoped
public class AudioLevelLightService implements IOverrideColorProviderProvider {
    static final long INTERVAL_MS = 50;
    private static final long IDLE_INTERVAL_MS = 500;
    private static final long UI_REFRESH_MS = 250;
    /** Quietest level shown, in dB below full scale; louder maps linearly up to 0 dB. */
    static final float FLOOR_DB = 60;
    /** How much of the previous level remains per sample, so the light falls back smoothly between beats. */
    static final float DECAY = 0.8f;
    /** Index of the logo among a device's lights (knobs and sliders count from 0). */
    private static final int LOGO = -1;
    /** A source naming an app rather than an audio device. */
    static final String APP_PREFIX = "app:";

    @Inject DeviceHolder devices;
    @Inject BaseLayerService baseLayer;
    @Inject ISndCtrl sndCtrl;
    @Inject AudioLevelMeter meter;
    @Inject CurveService curves;
    @Inject Event<VisualColorsChangedEvent> visualColorsChanged;

    private final ColorOverrideHolder holder = new ColorOverrideHolder();
    private final Map<String, Float> smoothed = new HashMap<>();
    private final Map<String, Long> uiRefreshedAt = new HashMap<>();
    private volatile boolean running;
    @Nullable private Thread thread;

    @PostConstruct
    void start() {
        running = true;
        thread = new Thread(this::run, "audio-level-lights");
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

    private void run() {
        while (running) {
            var active = false;
            try {
                active = tick();
            } catch (Throwable t) {
                log.warn("Audio-level lights failed to update", t);
            }
            try {
                Thread.sleep(active ? INTERVAL_MS : IDLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** One sample for every connected device; returns whether any audio-level light is showing. */
    private boolean tick() {
        var anyActive = false;
        AudioLevelMeter.Levels levels = null;
        for (var device : devices.all()) {
            var lc = lighting(device);
            var wanted = lc == null ? Map.<Integer, Light>of() : audioLevelLights(device, lc);
            if (!wanted.isEmpty() && meter.supported()) {
                anyActive = true;
                if (levels == null) {
                    levels = meter.sample();
                }
            }
            if (apply(device, lc, wanted, levels)) {
                relight(device, lc);
            }
        }
        return anyActive;
    }

    @Nullable
    private LightingConfig lighting(Device device) {
        try {
            var lc = device.lightingConfig();
            return lc != null && lc.lightingMode() == LightingMode.CUSTOM ? lc : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** An audio-level light: the control's dial actions, what it meters, and its quiet and loud colours. */
    private record Light(Commands commands, @Nullable String source, @Nullable String quiet, @Nullable String loud) {
    }

    /** The lights showing an audio level, by combined analog index; the logo is {@link #LOGO}. */
    private Map<Integer, Light> audioLevelLights(Device device, LightingConfig lc) {
        var serial = device.getSerialNumber();
        var effective = baseLayer.effectiveLighting(serial, lc);
        var dialData = baseLayer.effectiveDialData(serial, device.currentProfile());
        var result = new HashMap<Integer, Light>();
        var knobs = effective.knobConfigs();
        for (var i = 0; i < knobs.length; i++) {
            var k = knobs[i];
            if (k != null && k.getMode() == SINGLE_KNOB_MODE.AUDIO_LEVEL) {
                result.put(i, new Light(dialData.getOrDefault(i, Commands.EMPTY), k.getAudioLevelSource(), k.getColor2(), k.getColor1()));
            }
        }
        var sliders = effective.sliderConfigs();
        for (var j = 0; j < sliders.length; j++) {
            var sl = sliders[j];
            if (sl != null && sl.getMode() == SINGLE_SLIDER_MODE.AUDIO_LEVEL) {
                var idx = knobs.length + j;
                result.put(idx, new Light(dialData.getOrDefault(idx, Commands.EMPTY), sl.getAudioLevelSource(), sl.getColor2(), sl.getColor1()));
            }
        }
        var logo = effective.logoConfig();
        if (logo != null && logo.getMode() == SINGLE_LOGO_MODE.AUDIO_LEVEL) {
            result.put(LOGO, new Light(Commands.EMPTY, logo.getAudioLevelSource(), null, logo.getColor()));
        }
        return result;
    }

    /** Updates this device's overrides; returns whether any colour changed. */
    private boolean apply(Device device, @Nullable LightingConfig lc, Map<Integer, Light> lights, @Nullable AudioLevelMeter.Levels levels) {
        var serial = device.getSerialNumber();
        var knobLen = lc == null ? 0 : lc.knobConfigs().length;
        var count = knobLen + (lc == null ? 0 : lc.sliderConfigs().length);
        var changed = false;
        var sessions = levels == null ? null : sndCtrl.getAllSessions();
        var curve = curves.resolve(lc == null ? null : lc.getAudioLevelCurve());
        for (var i = LOGO; i < count; i++) {
            var light = lights.get(i);
            String color = null;
            if (light != null && levels != null) {
                var level = smooth(serial + '|' + i, (float) curve.apply(shape(peakFor(light, levels, sessions))));
                color = blend(light.quiet(), light.loud(), level);
            }
            if (i == LOGO) {
                changed |= setLogo(serial, color);
            } else if (i < knobLen) {
                changed |= setDial(serial, i, color);
            } else {
                changed |= setSlider(serial, i - knobLen, color);
            }
        }
        return changed;
    }

    private float peakFor(Light light, AudioLevelMeter.Levels levels, Collection<AudioSession> sessions) {
        var source = StringUtils.trimToEmpty(light.source());
        if (source.startsWith(APP_PREFIX)) {
            var app = source.substring(APP_PREFIX.length());
            return sessionPeak(levels, sessions, s -> s.matches(app));
        }
        if (!source.isEmpty()) {
            // An exact name wins: Wave Link names virtual devices after what they carry, so names contain each other.
            var devices = sndCtrl.devices();
            var exact = StreamEx.of(devices).filter(d -> StringUtils.equalsIgnoreCase(d.name(), source)).toList();
            return StreamEx.of(exact.isEmpty() ? StreamEx.of(devices).filter(d -> StringUtils.containsIgnoreCase(d.name(), source)).toList() : exact)
                           .map(d -> levels.device(d.id()))
                           .max(Float::compare).orElse(0f);
        }
        for (var cmd : light.commands().getCommands()) {
            if (cmd instanceof CommandVolumeProcess p && p.getProcessName() != null) {
                // An app group is metered as every app: a light has no notion of which apps another control claims.
                var names = p.getProcessName().stream().anyMatch(EverythingElse::isToken) ? null : p.getProcessName();
                return sessionPeak(levels, sessions, s -> names == null ? !s.isSystemSounds() : names.stream().anyMatch(s::matches));
            }
            if (cmd instanceof CommandVolumeDevice d) {
                return StringUtils.isBlank(d.getDeviceId()) ? levels.defaultOutput() : levels.device(d.getDeviceId());
            }
            if (cmd instanceof CommandVolumeFocus) {
                var focus = sndCtrl.getFocusApplication();
                return StringUtils.isBlank(focus) ? 0f
                        : sessionPeak(levels, sessions, s -> s.executable() != null && StringUtils.equalsIgnoreCase(s.executable().getPath(), focus) || s.matches(focus));
            }
        }
        return levels.defaultOutput();
    }

    private static float sessionPeak(AudioLevelMeter.Levels levels, Collection<AudioSession> sessions, Predicate<AudioSession> which) {
        return StreamEx.of(sessions).filter(which).map(levels::session).max(Float::compare).orElse(0f);
    }

    /** A linear peak as 0..1 on a dB scale from {@link #FLOOR_DB} below full scale up to full scale. */
    static float shape(float peak) {
        if (peak <= 0) {
            return 0;
        }
        var db = 20 * (float) Math.log10(peak);
        return Math.max(0, Math.min(1, (db + FLOOR_DB) / FLOOR_DB));
    }

    /** Rises at once, falls back by {@link #DECAY} per sample. */
    private float smooth(String key, float level) {
        var next = Math.max(level, smoothed.getOrDefault(key, 0f) * DECAY);
        smoothed.put(key, next);
        return next;
    }

    /** {@code quiet} to {@code loud} at {@code t}; a blank quiet colour is off (black). */
    static String blend(@Nullable String quiet, @Nullable String loud, float t) {
        var a = parse(quiet, 0x000000);
        var b = parse(loud, 0xFFFFFF);
        var out = 0;
        for (var shift = 16; shift >= 0; shift -= 8) {
            var from = (a >> shift) & 0xFF;
            var to = (b >> shift) & 0xFF;
            out |= Math.round(from + (to - from) * t) << shift;
        }
        return String.format("#%06X", out);
    }

    /** {@code #RRGGBB} (the leading # optional) as an int; plain AWT-free parsing, as macOS has no AWT. */
    private static int parse(@Nullable String hex, int fallback) {
        var digits = StringUtils.removeStart(StringUtils.trimToEmpty(hex), "#");
        if (digits.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean setDial(String serial, int idx, @Nullable String color) {
        var current = holder.getDialOverride(serial, idx).map(SingleKnobLightingConfig::getColor1).orElse(null);
        if (Objects.equals(current, color)) {
            return false;
        }
        holder.setDialOverride(serial, idx, color == null ? null : new SingleKnobLightingConfig().setMode(SINGLE_KNOB_MODE.STATIC).setColor1(color));
        return true;
    }

    private boolean setLogo(String serial, @Nullable String color) {
        var current = holder.getLogoOverride(serial).map(SingleLogoLightingConfig::getColor).orElse(null);
        if (Objects.equals(current, color)) {
            return false;
        }
        holder.setLogoOverride(serial, color == null ? null : new SingleLogoLightingConfig().setMode(SINGLE_LOGO_MODE.STATIC).setColor(color));
        return true;
    }

    private boolean setSlider(String serial, int idx, @Nullable String color) {
        var current = holder.getSliderOverride(serial, idx).map(SingleSliderLightingConfig::getColor1).orElse(null);
        if (Objects.equals(current, color)) {
            return false;
        }
        holder.setSliderOverride(serial, idx, color == null ? null : new SingleSliderLightingConfig().setMode(SINGLE_SLIDER_MODE.STATIC).setColor1(color));
        return true;
    }

    private void relight(Device device, @Nullable LightingConfig lc) {
        var serial = device.getSerialNumber();
        try {
            device.setLighting(lc != null ? lc : device.lightingConfig(), true);
        } catch (Exception e) {
            log.debug("Unable to re-send audio-level lighting for {}", serial, e);
            return;
        }
        // The panel follows every sample; the on-screen device is refreshed a few times a second.
        var now = System.currentTimeMillis();
        if (now - uiRefreshedAt.getOrDefault(serial, 0L) >= UI_REFRESH_MS) {
            uiRefreshedAt.put(serial, now);
            visualColorsChanged.fire(new VisualColorsChangedEvent(serial));
        }
    }
}
