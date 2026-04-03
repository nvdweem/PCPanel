package com.getpcpanel.commands.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.Role;
import com.getpcpanel.cpp.windows.SndCtrlWindows;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandVolumeDefaultDeviceToggleAdvanced extends CommandVolume implements ButtonAction {
    private final List<DeviceSet> devices;
    private int currentIdx = -1;

    @JsonCreator
    public CommandVolumeDefaultDeviceToggleAdvanced(@JsonProperty("devices") List<DeviceSet> devices) {
        this.devices = Objects.requireNonNullElseGet(devices, ArrayList::new);
    }

    @Override
    public void execute() {
        if (devices.isEmpty() || !(getSndCtrl() instanceof SndCtrlWindows)) {
            return;
        }
        determineIndex();
        currentIdx++;
        setDevices(devices.get(currentIdx % devices.size()));
    }

    @Override
    public String buildLabel() {
        return StreamEx.of(devices).map(DeviceSet::name).remove(StringUtils::isBlank).joining(", ");
    }

    private void determineIndex() {
        if (currentIdx != -1) {
            return;
        }

        var currentMediaPlayback = getDefaultDeviceName(SndCtrlWindows.DefaultFor.mediaPlayback);
        var currentMediaRecord = getDefaultDeviceName(SndCtrlWindows.DefaultFor.mediaRecord);
        var currentCommunicationPlayback = getDefaultDeviceName(SndCtrlWindows.DefaultFor.communicationPlayback);
        var currentCommunicationRecord = getDefaultDeviceName(SndCtrlWindows.DefaultFor.communicationRecord);

        currentIdx = EntryStream.of(devices).filterValues(d ->
                (currentMediaPlayback == null || StringUtils.containsIgnoreCase(currentMediaPlayback, d.mediaPlayback())) &&
                        (currentMediaRecord == null || StringUtils.containsIgnoreCase(currentMediaRecord, d.mediaRecord())) &&
                        (currentCommunicationPlayback == null || StringUtils.containsIgnoreCase(currentCommunicationPlayback, d.communicationPlayback())) &&
                        (currentCommunicationRecord == null || StringUtils.containsIgnoreCase(currentCommunicationRecord, d.communicationRecord()))
        ).keys().findFirst().orElse(0);
    }

    private @Nullable String getDefaultDeviceName(SndCtrlWindows.DefaultFor defaultFor) {
        var sndCtrl = getWinSndCtrl();
        var def = sndCtrl.getDefaults().get(defaultFor);
        return Optional.ofNullable(sndCtrl.getDevice(def)).map(AudioDevice::name).orElse(null);
    }

    private void setDevices(DeviceSet deviceSet) {
        var sndCtrl = getWinSndCtrl();
        sndCtrl.setDefaultDevice(deviceSet.mediaPlayback(), DataFlow.dfRender, Role.roleMultimedia);
        sndCtrl.setDefaultDevice(deviceSet.mediaRecord(), DataFlow.dfCapture, Role.roleMultimedia);
        sndCtrl.setDefaultDevice(deviceSet.communicationPlayback(), DataFlow.dfRender, Role.roleCommunications);
        sndCtrl.setDefaultDevice(deviceSet.communicationRecord(), DataFlow.dfCapture, Role.roleCommunications);
    }

    private SndCtrlWindows getWinSndCtrl() {
        return (SndCtrlWindows) getSndCtrl();
    }

    @Nullable
    @Override
    public String getOverlayText() {
        if (currentIdx == -1) {
            return null;
        }
        return devices.get(currentIdx % devices.size()).name();
    }
}
