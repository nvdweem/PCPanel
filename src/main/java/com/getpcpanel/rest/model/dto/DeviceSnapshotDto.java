package com.getpcpanel.rest.model.dto;

import java.util.List;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.LightGroupKind;
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
        DeviceDescriptor descriptor,
        String currentProfile,
        List<String> profiles,
        // ── extra snapshot fields ────────────────────────────────────────────
        LightingConfig lightingConfig,
        ProfileSnapshotDto currentProfileSnapshot,
        // The device's base-layer profile assignments (null when none is flagged, or it is the active
        // profile). Lets the UI show base-layer fallbacks per slot and edit them in place.
        @Nullable ProfileSnapshotDto baseLayerSnapshot,
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
        var descriptor = device.descriptor();
        var profile = device.currentProfile();
        // PCPanel devices report counts/kind from the legacy enum (unchanged); descriptor-only
        // devices (Deej, generic) have no DeviceType, so derive everything from the descriptor.
        var analogCount = dt != null ? dt.getAnalogCount() : descriptor.analogInputs().size();
        var buttonCount = dt != null ? dt.getButtonCount() : descriptor.digitalInputs().size();
        var deviceTypeName = dt != null ? dt.name() : descriptor.deviceKindId();
        // Mirror DeviceDto.from: a descriptor-only device (no DeviceType) derives logo-LED presence from
        // its descriptor's light outputs, so REST and WS snapshots agree for a future lit generic device.
        var hasLogoLed = dt != null ? dt.isHasLogoLed()
                : StreamEx.of(descriptor.lightOutputs()).anyMatch(l -> l.group() == LightGroupKind.LOGO);
        var visualColors = proVisualColorsService.resolve(device);
        // The base layer fills in for controls the active profile leaves blank; surface it so the UI
        // can show those fallbacks (and edit them). Skip it when the base layer IS the active profile.
        var baseLayer = StreamEx.of(deviceSave.getProfiles()).findFirst(Profile::isBaseLayer).filter(b -> b != profile).orElse(null);

        var knobValues = IntStream.range(0, analogCount)
                                  .mapToObj(device::getKnobRotation)
                                  .toList();

        return new DeviceSnapshotDto(
                device.getSerialNumber(),
                device.getDisplayName(),
                deviceTypeName,
                analogCount,
                buttonCount,
                hasLogoLed,
                descriptor,
                deviceSave.getCurrentProfileName(),
                StreamEx.of(deviceSave.getProfiles()).map(Profile::getName).toList(),
                device.getSavedLightingConfig(),
                ProfileSnapshotDto.from(profile),
                baseLayer == null ? null : ProfileSnapshotDto.from(baseLayer),
                knobValues,
                visualColors.dialColors(),
                visualColors.sliderLabelColors(),
                visualColors.sliderColors(),
                visualColors.logoColor()
        );
    }
}
