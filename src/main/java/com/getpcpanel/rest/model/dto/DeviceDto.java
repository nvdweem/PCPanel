package com.getpcpanel.rest.model.dto;

import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.device.descriptor.LightGroupKind;
import com.getpcpanel.profile.DeviceSave;

import one.util.streamex.StreamEx;

public record DeviceDto(
        String serial,
        String displayName,
        @Nullable DeviceType deviceType,
        int analogCount,
        int buttonCount,
        boolean hasLogoLed,
        @Nullable DeviceDescriptor descriptor,
        boolean connected,
        String currentProfile,
        List<String> profiles
) {
    public static DeviceDto from(Device device, DeviceSave deviceSave) {
        var type = device.deviceType();
        return new DeviceDto(
                device.getSerialNumber(),
                device.getDisplayName(),
                type,
                type.getAnalogCount(),
                type.getButtonCount(),
                type.isHasLogoLed(),
                device.descriptor(),
                true,
                deviceSave.getCurrentProfileName(),
                StreamEx.of(deviceSave.getProfiles()).map(p -> p.getName()).toList()
        );
    }

    /**
     * Builds a DTO for a persisted-but-not-live device from its saved capability snapshot, so the UI
     * can render a known device's config + lighting while it is unplugged. Counts come from the
     * persisted descriptor; if it is null (a legacy save not yet back-filled at connect) they fall
     * back to 0. {@code connected} is always false here.
     */
    public static DeviceDto from(String serial, DeviceSave deviceSave, @Nullable DeviceDescriptor descriptor) {
        return new DeviceDto(
                serial,
                deviceSave.getDisplayName(),
                deviceTypeOf(descriptor),
                descriptor == null ? 0 : descriptor.analogInputs().size(),
                descriptor == null ? 0 : descriptor.digitalInputs().size(),
                descriptor != null && StreamEx.of(descriptor.lightOutputs()).anyMatch(l -> l.group() == LightGroupKind.LOGO),
                descriptor,
                false,
                deviceSave.getCurrentProfileName(),
                StreamEx.of(deviceSave.getProfiles()).map(p -> p.getName()).toList()
        );
    }

    @Nullable
    private static DeviceType deviceTypeOf(@Nullable DeviceDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        try {
            return DeviceType.valueOf(descriptor.deviceKindId());
        } catch (IllegalArgumentException e) {
            return null; // Non-PCPanel device kind: no legacy DeviceType enum value.
        }
    }
}
