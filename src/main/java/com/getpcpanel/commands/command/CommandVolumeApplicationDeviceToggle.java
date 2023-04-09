package com.getpcpanel.commands.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.DeviceSet;
import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.DataFlow;
import com.getpcpanel.cpp.windows.SndCtrlWindows;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Getter
@Log4j2
@ToString(callSuper = true)
public class CommandVolumeApplicationDeviceToggle extends CommandVolume implements ButtonAction {
    private final List<String> processes;
    private final boolean followFocus;
    private final List<DeviceSet> devices;
    private int currentIdx;

    @JsonCreator
    public CommandVolumeApplicationDeviceToggle(@JsonProperty("processes") List<String> processes, @JsonProperty("followFocus") boolean followFocus, @JsonProperty("devices") List<DeviceSet> devices) {
        this.processes = Objects.requireNonNullElseGet(processes, ArrayList::new);
        this.followFocus = followFocus;
        this.devices = Objects.requireNonNullElseGet(devices, ArrayList::new);
    }

    @Override
    public void execute() {
        if (devices.isEmpty() || !(getSndCtrl() instanceof SndCtrlWindows)) {
            return;
        }
        currentIdx++;
        setDevices(devices.get(currentIdx % devices.size()));
    }

    @Override
    public String buildLabel() {
        return (followFocus ? "Focus process" : processes.size() + " processes") + ", " + devices.size() + " devices";
    }

    private void setDevices(DeviceSet deviceSet) {
        var sndCtrl = getWinSndCtrl();

        determineTargets().forEach(pid -> {
            sndCtrl.setDeviceForProcess(pid, DataFlow.dfRender, deviceIdFor(deviceSet.mediaPlayback()));
            sndCtrl.setDeviceForProcess(pid, DataFlow.dfCapture, deviceIdFor(deviceSet.mediaRecord()));
        });
    }

    private @Nullable String deviceIdFor(@Nullable String mediaPlayback) {
        if (StringUtils.isBlank(mediaPlayback)) {
            return null;
        }
        var sndCtrl = getWinSndCtrl();
        return StreamEx.of(sndCtrl.getDevices()).findFirst(d -> StringUtils.containsIgnoreCase(d.name(), mediaPlayback)).map(AudioDevice::id).orElse(null);
    }

    private Set<Integer> determineTargets() {
        var sndCtrl = getWinSndCtrl();
        if (isFollowFocus()) {
            var focus = sndCtrl.getFocusApplication();
            return sndCtrl.getPidsFor(focus);
        }

        return StreamEx.of(processes).flatCollection(sndCtrl::getPidsFor).toSet();
    }

    private SndCtrlWindows getWinSndCtrl() {
        return (SndCtrlWindows) getSndCtrl();
    }

    @Nullable
    @Override
    public String getOverlayText() {
        if (currentIdx == -1 && !devices.isEmpty() || devices.size() == 0) {
            return null;
        }
        return devices.get(currentIdx % devices.size()).name();
    }
}
