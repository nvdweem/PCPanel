package com.getpcpanel.rest.dto;

import java.util.List;

import com.getpcpanel.device.Device;
import com.getpcpanel.profile.DeviceSave;

import one.util.streamex.StreamEx;

public record DeviceDto(
        String serial,
        String displayName,
        String deviceType,
        int analogCount,
        int buttonCount,
        boolean hasLogoLed,
        String currentProfile,
        List<String> profiles
) {
    public static DeviceDto from(Device device, DeviceSave deviceSave) {
        var type = device.deviceType();
        return new DeviceDto(
                device.getSerialNumber(),
                device.getDisplayName(),
                type.name(),
                type.getAnalogCount(),
                type.getButtonCount(),
                type.isHasLogoLed(),
                deviceSave.getCurrentProfileName(),
                StreamEx.of(deviceSave.getProfiles()).map(p -> p.getName()).toList()
        );
    }
}
