package com.getpcpanel.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.getpcpanel.commands.Commands;
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Resolves a control's effective configuration against the device's "base layer" — the profile flagged
 * {@link Profile#isBaseLayer()}. The base layer fills in for whatever the active profile leaves blank:
 *
 * <ul>
 *   <li><b>Commands</b> — a control with no command in the active profile falls back to the base layer's
 *       command for that control (works regardless of either profile's lighting mode).</li>
 *   <li><b>Lighting</b> — in per-control (CUSTOM) mode, any control whose active per-control lighting is
 *       {@code NONE} (off) falls back to the base layer's per-control lighting, mute-override colour
 *       included. Global lighting modes have no per-control "off" to fill, so they pass through.</li>
 * </ul>
 *
 * The active profile always wins where it is configured; the base layer never overrides it. The instance
 * methods resolve the base layer from persistence; the static helpers hold the merge logic.
 */
@Log4j2
@ApplicationScoped
public class BaseLayerService {
    @Inject
    SaveService saveService;

    /** The device's base-layer profile, if one is flagged. */
    public Optional<Profile> baseLayer(String serial) {
        if (saveService == null) {
            return Optional.empty();
        }
        var save = saveService.get();
        if (save == null) {
            return Optional.empty();
        }
        var device = save.getDevices().get(serial);
        if (device == null) {
            return Optional.empty();
        }
        return StreamEx.of(device.getProfiles()).findFirst(Profile::isBaseLayer);
    }

    /** The base layer to use as a fallback for {@code active} — the flagged one, unless it IS active. */
    private @Nullable Profile fallbackFor(String serial, Profile active) {
        return baseLayer(serial).filter(b -> b != active).orElse(null);
    }

    // ── instance API (resolves the base layer from persistence) ──────────────────
    @Nullable
    public Commands effectiveDial(String serial, Profile active, int knob) {
        return effectiveDial(active, fallbackFor(serial, active), knob);
    }

    public Commands effectiveButton(String serial, Profile active, int button) {
        return effectiveButton(active, fallbackFor(serial, active), button);
    }

    @Nullable
    public Commands effectiveDblButton(String serial, Profile active, int button) {
        return effectiveDblButton(active, fallbackFor(serial, active), button);
    }

    @Nullable
    public Commands effectiveReleaseButton(String serial, Profile active, int button) {
        return effectiveReleaseButton(active, fallbackFor(serial, active), button);
    }

    public Map<Integer, Commands> effectiveDialData(String serial, Profile active) {
        return effectiveDialData(active, fallbackFor(serial, active));
    }

    /**
     * The lighting to actually display/send: the active config with every {@code NONE} per-control slot
     * filled from the base layer. Merging the base into its own config is a no-op, so the active profile
     * needn't be excluded here.
     */
    public LightingConfig effectiveLighting(String serial, @Nullable LightingConfig active) {
        return mergeLighting(active, baseLayer(serial).map(Profile::lightingConfig).orElse(null));
    }

    // ── static merge logic (no persistence; the unit-test seam) ──────────────────
    @Nullable
    static Commands effectiveDial(Profile active, @Nullable Profile base, int knob) {
        var own = active.getDialData(knob);
        if (Commands.hasCommands(own) || base == null) {
            return own;
        }
        var fallback = base.getDialData(knob);
        return Commands.hasCommands(fallback) ? fallback : own;
    }

    static Commands effectiveButton(Profile active, @Nullable Profile base, int button) {
        var own = active.getButtonData(button);
        if (Commands.hasCommands(own) || base == null) {
            return own;
        }
        var fallback = base.getButtonData(button);
        return Commands.hasCommands(fallback) ? fallback : own;
    }

    @Nullable
    static Commands effectiveDblButton(Profile active, @Nullable Profile base, int button) {
        var own = active.getDblButtonData(button);
        if (Commands.hasCommands(own) || base == null) {
            return own;
        }
        var fallback = base.getDblButtonData(button);
        return Commands.hasCommands(fallback) ? fallback : own;
    }

    @Nullable
    static Commands effectiveReleaseButton(Profile active, @Nullable Profile base, int button) {
        var own = active.getReleaseButtonData(button);
        if (Commands.hasCommands(own) || base == null) {
            return own;
        }
        var fallback = base.getReleaseButtonData(button);
        return Commands.hasCommands(fallback) ? fallback : own;
    }

    /** Active dial assignments with the base layer merged underneath (active wins per control). */
    static Map<Integer, Commands> effectiveDialData(Profile active, @Nullable Profile base) {
        if (base == null) {
            // Defensive copy: both branches must return a caller-owned map. Returning the Profile's live
            // backing map here would let a mutating caller corrupt the persisted profile's dialData.
            return new LinkedHashMap<>(active.getDialData());
        }
        var merged = new LinkedHashMap<>(base.getDialData());
        active.getDialData().forEach((k, v) -> {
            if (Commands.hasCommands(v)) {
                merged.put(k, v);
            }
        });
        return merged;
    }

    /**
     * Fills every {@code NONE} per-control slot of {@code active} from {@code base}. Only meaningful in
     * CUSTOM mode; other modes (and the no-base case) are returned unchanged. The result shares the base
     * layer's per-control config objects, which every consumer treats as read-only.
     */
    static LightingConfig mergeLighting(@Nullable LightingConfig active, @Nullable LightingConfig base) {
        if (active == null || base == null || active.lightingMode() != LightingMode.CUSTOM) {
            return active;
        }
        var merged = active.deepCopy();
        fillKnobs(merged.knobConfigs(), base.knobConfigs());
        fillSliders(merged.sliderConfigs(), base.sliderConfigs());
        fillLabels(merged.sliderLabelConfigs(), base.sliderLabelConfigs());
        if (isNone(merged.logoConfig()) && !isNone(base.logoConfig())) {
            return merged.toBuilder().logoConfig(base.logoConfig()).build();
        }
        return merged;
    }

    private static void fillKnobs(SingleKnobLightingConfig[] target, SingleKnobLightingConfig[] base) {
        for (var i = 0; i < target.length && i < base.length; i++) {
            if (isNone(target[i]) && !isNone(base[i])) {
                target[i] = base[i];
            }
        }
    }

    private static void fillSliders(SingleSliderLightingConfig[] target, SingleSliderLightingConfig[] base) {
        for (var i = 0; i < target.length && i < base.length; i++) {
            if (isNone(target[i]) && !isNone(base[i])) {
                target[i] = base[i];
            }
        }
    }

    private static void fillLabels(SingleSliderLabelLightingConfig[] target, SingleSliderLabelLightingConfig[] base) {
        for (var i = 0; i < target.length && i < base.length; i++) {
            if (isNone(target[i]) && !isNone(base[i])) {
                target[i] = base[i];
            }
        }
    }

    private static boolean isNone(@Nullable SingleKnobLightingConfig c) {
        return c == null || c.getMode() == null || c.getMode() == SINGLE_KNOB_MODE.NONE;
    }

    private static boolean isNone(@Nullable SingleSliderLightingConfig c) {
        return c == null || c.getMode() == null || c.getMode() == SINGLE_SLIDER_MODE.NONE;
    }

    private static boolean isNone(@Nullable SingleSliderLabelLightingConfig c) {
        return c == null || c.getMode() == null || c.getMode() == SINGLE_SLIDER_LABEL_MODE.NONE;
    }

    private static boolean isNone(@Nullable SingleLogoLightingConfig c) {
        return c == null || c.getMode() == null || c.getMode() == SINGLE_LOGO_MODE.NONE;
    }
}
