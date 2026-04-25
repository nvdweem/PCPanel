package com.getpcpanel.rest.model.dto;

import java.util.List;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.device.Device;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.rest.ProVisualColorsService;
import com.getpcpanel.rest.model.ws.WsEvent;

import one.util.streamex.StreamEx;

/**
 * Full device state snapshot sent over WebSocket on connection.
 * Combines DeviceDto fields with lighting config, the active profile's
 * assignments, and the current analog knob values — so the frontend
 * never needs separate HTTP calls just to display device state.
 */
@JsonTypeName("device_snapshot")
public record DeviceSnapshotDto(
        // ── core device fields (same as DeviceDto) ──────────────────────────
        String serial,
        String displayName,
        String deviceType,
        int analogCount,
        int buttonCount,
        boolean hasLogoLed,
        String currentProfile,
        List<String> profiles,
        // ── extra snapshot fields ────────────────────────────────────────────
        LightingConfig lightingConfig,
        ProfileSnapshotDto currentProfileSnapshot,
        List<Integer> analogValues,
        List<String> dialColors,
        List<String> sliderLabelColors,
        List<List<String>> sliderColors,
        String logoColor
) implements WsEvent {
    /**
     * WsEvent type discriminator understood by the frontend.
     */
    public String type() {
        return "device_snapshot";
    }

    public static DeviceSnapshotDto from(Device device, DeviceSave deviceSave, ProVisualColorsService proVisualColorsService) {
        var dt = device.deviceType();
        var profile = device.currentProfile();
        var analogCount = dt.getAnalogCount();
        var visualColors = proVisualColorsService.resolve(device);

        var knobValues = IntStream.range(0, analogCount)
                                  .mapToObj(device::getKnobRotation)
                                  .toList();

        return new DeviceSnapshotDto(
                device.getSerialNumber(),
                device.getDisplayName(),
                dt.name(),
                analogCount,
                dt.getButtonCount(),
                dt.isHasLogoLed(),
                deviceSave.getCurrentProfileName(),
                StreamEx.of(deviceSave.getProfiles()).map(Profile::getName).toList(),
                device.getSavedLightingConfig(),
                ProfileSnapshotDto.from(profile),
                knobValues,
                visualColors.dialColors(),
                visualColors.sliderLabelColors(),
                visualColors.sliderColors(),
                visualColors.logoColor()
        );
    }
}
