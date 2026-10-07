package com.getpcpanel.integration.volume.platform.linux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;

import dev.niels.pulse.ChannelVolumes;
import dev.niels.pulse.model.DeviceInfo;
import dev.niels.pulse.model.StreamInfo;

/**
 * Turns what the protocol client reads into the {@link PulseAudioTarget}s that {@code pactl list} parsing produces: the
 * same header fields ({@code Name}, {@code Mute}, {@code Volume}, {@code Sink}, …) written the way pactl prints them,
 * and the property list as is. So every reader of a target works the same whichever of the two produced it.
 */
final class NativePulseTargets {
    private NativePulseTargets() {
    }

    static PulseAudioTarget device(DeviceInfo device, InOutput type, boolean isDefault) {
        var metas = new HashMap<String, String>();
        metas.put("Name", device.name());
        putIfPresent(metas, "Description", device.description());
        putIfPresent(metas, "Driver", device.driver());
        metas.put("Mute", yesNo(device.muted()));
        metas.put("Volume", volume(device.volume()));
        metas.put(type == InOutput.output ? "Monitor Source" : "Monitor of Sink", device.monitorName() == null ? "n/a" : device.monitorName());
        putIfPresent(metas, "Active Port", device.activePort());
        return target(device.index(), isDefault, metas, device.properties(), type);
    }

    static PulseAudioTarget stream(StreamInfo stream, InOutput type) {
        var metas = new HashMap<String, String>();
        metas.put(type == InOutput.session ? "Sink" : "Source", String.valueOf(stream.device()));
        metas.put("Client", String.valueOf(stream.client()));
        putIfPresent(metas, "Driver", stream.driver());
        metas.put("Corked", yesNo(stream.corked()));
        metas.put("Mute", yesNo(stream.muted()));
        if (stream.volume() != null) {
            metas.put("Volume", volume(stream.volume()));
        }
        return target(stream.index(), false, metas, stream.properties(), type);
    }

    static List<PulseAudioTarget> devices(List<DeviceInfo> devices, InOutput type, @Nullable String defaultName) {
        return devices.stream().map(d -> device(d, type, d.name() != null && d.name().equals(defaultName))).toList();
    }

    static List<PulseAudioTarget> streams(List<StreamInfo> streams, InOutput type) {
        return streams.stream().map(s -> stream(s, type)).toList();
    }

    /** {@code pactl}'s "{@code 0: 65536 / 100%, 1: …}"; readers take the raw value of the first channel. */
    static String volume(ChannelVolumes volume) {
        return IntStream.range(0, volume.channels())
                        .mapToObj(i -> i + ": " + volume.get(i) + " / " + Math.round(volume.get(i) * 100.0 / ChannelVolumes.NORM) + "%")
                        .collect(Collectors.joining(",   "));
    }

    private static PulseAudioTarget target(int index, boolean isDefault, Map<String, String> metas, Map<String, String> properties, InOutput type) {
        return PulseAudioTarget.builder().index(index).isDefault(isDefault).metas(metas).properties(new HashMap<>(properties)).type(type).build();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static void putIfPresent(Map<String, String> metas, String key, @Nullable String value) {
        if (value != null) {
            metas.put(key, value);
        }
    }
}
