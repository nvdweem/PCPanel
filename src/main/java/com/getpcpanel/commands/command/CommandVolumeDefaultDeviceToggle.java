package com.getpcpanel.commands.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandVolumeDefaultDeviceToggle extends CommandVolume implements ButtonAction {
    private final List<String> devices;
    private int currentIdx; // Used as a fallback for when the current idx cannot be found

    @JsonCreator
    public CommandVolumeDefaultDeviceToggle(@JsonProperty("devices") List<String> devices) {
        this.devices = Objects.requireNonNullElseGet(devices, ArrayList::new);
    }

    @Override
    public void execute() {
        var firstDevice = firstAudioDevice(); // To determine the type (input/output)
        var currentDevice = currentAudioDevice(firstDevice.map(AudioDevice::dataflow).map(df -> df == DataFlow.dfRender).orElse(true));
        currentIdx = (currentDevice.map(AudioDevice::id).map(devices::indexOf).orElse(currentIdx) + 1) % devices.size();

        var nextDeviceId = devices.get(currentIdx);
        getSndCtrl().setDefaultDevice(nextDeviceId);
    }

    private Optional<AudioDevice> firstAudioDevice() {
        return StreamEx.of(devices).map(getSndCtrl().getDevicesMap()::get).nonNull().findFirst();
    }

    private Optional<AudioDevice> currentAudioDevice(boolean output) {
        var sndCtrl = getSndCtrl();
        return Optional.ofNullable(sndCtrl.getDevice(output ? sndCtrl.defaultPlayer() : sndCtrl.defaultRecorder()));
    }
}
