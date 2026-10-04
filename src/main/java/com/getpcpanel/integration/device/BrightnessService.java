package com.getpcpanel.integration.device;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.curve.CurveService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.integration.device.command.CommandBrightness;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.KnobSetting;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Resolves the device's <em>runtime</em> global brightness: as soon as any analog input — in any profile,
 * not just the active/main one — has a {@link CommandBrightness}, that control's live physical position
 * drives the global brightness and wins over each profile's saved {@code globalBrightness}. This makes
 * brightness a single device-wide runtime value that survives profile switches instead of snapping to the
 * newly-activated profile's stored value.
 *
 * <p>When several brightness controls exist, one is chosen deterministically — preferring a logarithmic
 * curve (finer low-end control), then the lowest analog index — so the behaviour is predictable.
 *
 * <p>A button can set the brightness too ({@link #setButtonBrightness}, from "Run dial actions at a level"): that value wins
 * until the brightness dial on the same device moves again ({@link #clearButtonBrightness}). It lives in memory only.
 *
 * <p>The {@code OptionalInt} is empty when neither a button nor a brightness control sets it, in which case the saved
 * {@code globalBrightness} is used as before.
 */
@Log4j2
@ApplicationScoped
public class BrightnessService {
    @Inject
    SaveService saveService;
    @Inject
    DeviceHolder devices;
    @Inject
    CurveService curves;
    private final Map<String, Integer> buttonBrightness = new ConcurrentHashMap<>();

    /** Sets the device's brightness (0-100) from a button; it wins over the brightness dial until that moves. */
    public void setButtonBrightness(String serial, int percent) {
        buttonBrightness.put(serial, Math.clamp(percent, 0, 100));
    }

    /** Hands the device's brightness back to its brightness dial (or the saved value). */
    public void clearButtonBrightness(String serial) {
        buttonBrightness.remove(serial);
    }

    /** The runtime global brightness (0-100) for a device, or empty when neither a button nor an analog input sets it. */
    public OptionalInt runtimeBrightness(String serial) {
        var fromButton = buttonBrightness.get(serial);
        if (fromButton != null) {
            return OptionalInt.of(fromButton);
        }
        if (saveService == null || saveService.get() == null) {
            return OptionalInt.empty();
        }
        var deviceSave = saveService.get().getDevices().get(serial);
        if (deviceSave == null) {
            return OptionalInt.empty();
        }
        var control = bestBrightnessControl(deviceSave.getProfiles()).orElse(null);
        var device = devices.getDevice(serial).orElse(null);
        if (control == null || device == null) {
            return OptionalInt.empty();
        }
        if (!device.hasKnobRotation(control.index())) {
            // The dial hasn't reported a real position yet (e.g. lighting pushed synchronously on connect,
            // before the first knob read). Fall back to the saved globalBrightness instead of reading the
            // default 0, which would send the whole device dark until the first read lands.
            return OptionalInt.empty();
        }
        var raw = device.getKnobRotation(control.index());
        var value = curves.calculatorFor(control.knobSetting()).calcValue(control.command(), raw, 0f, 100f);
        return OptionalInt.of(Math.round(value));
    }

    /**
     * The brightness control to read, picked deterministically across all the device's profiles: one
     * carrying a response curve wins over a straight-through one, then the lowest analog index. Static +
     * side-effect-free so the ordering is unit-testable.
     */
    static Optional<BrightnessControl> bestBrightnessControl(List<Profile> profiles) {
        return profiles.stream()
                       .flatMap(p -> p.getDialData().entrySet().stream()
                                      .map(e -> e.getValue().getCommand(CommandBrightness.class)
                                                 .map(cmd -> new BrightnessControl(e.getKey(), cmd, p.getKnobSettings().get(e.getKey())))
                                                 .orElse(null))
                                      .filter(Objects::nonNull))
                       .min(Comparator.comparing((BrightnessControl b) -> !b.shaped())
                                      .thenComparingInt(BrightnessControl::index));
    }

    record BrightnessControl(int index, CommandBrightness command, @Nullable KnobSetting knobSetting) {
        /** Carries a response curve rather than running straight through — finer low-end control. */
        boolean shaped() {
            return knobSetting != null && StringUtils.isNotBlank(knobSetting.getCurve());
        }
    }
}
