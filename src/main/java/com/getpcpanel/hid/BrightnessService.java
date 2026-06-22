package com.getpcpanel.hid;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.CommandBrightness;
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
 * <p>The {@code OptionalInt} is empty when no brightness control is configured, in which case the saved
 * {@code globalBrightness} is used as before.
 */
@Log4j2
@ApplicationScoped
public class BrightnessService {
    @Inject
    SaveService saveService;
    @Inject
    DeviceHolder devices;

    /** The runtime global brightness (0-100) for a device, or empty when no analog input controls it. */
    public OptionalInt runtimeBrightness(String serial) {
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
        var raw = device.getKnobRotation(control.index());
        var value = new DialValueCalculator(control.knobSetting()).calcValue(control.command(), raw, 0f, 100f);
        return OptionalInt.of(Math.round(value));
    }

    /**
     * The brightness control to read, picked deterministically across all the device's profiles: a
     * logarithmic one wins over a linear one, then the lowest analog index. Static + side-effect-free so
     * the ordering is unit-testable.
     */
    static Optional<BrightnessControl> bestBrightnessControl(List<Profile> profiles) {
        return profiles.stream()
                       .flatMap(p -> p.getDialData().entrySet().stream()
                                      .map(e -> e.getValue().getCommand(CommandBrightness.class)
                                                 .map(cmd -> new BrightnessControl(e.getKey(), cmd, p.getKnobSettings().get(e.getKey())))
                                                 .orElse(null))
                                      .filter(Objects::nonNull))
                       .min(Comparator.comparing((BrightnessControl b) -> !b.logarithmic())
                                      .thenComparingInt(BrightnessControl::index));
    }

    record BrightnessControl(int index, CommandBrightness command, @Nullable KnobSetting knobSetting) {
        boolean logarithmic() {
            return knobSetting != null && knobSetting.isLogarithmic();
        }
    }
}
