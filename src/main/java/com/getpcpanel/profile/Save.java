package com.getpcpanel.profile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.device.DeviceType;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
public class Save {
    private Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    private boolean obsEnabled;
    private String obsAddress = "localhost";
    private String obsPort = "4444";
    private String obsPassword;
    private boolean voicemeeterEnabled;
    private String voicemeeterPath = "C:\\Program Files (x86)\\VB\\Voicemeeter";
    @Nullable private Integer preventSliderTwitchDelay;
    @Nullable private Integer sliderRollingAverage;
    @Nullable private Integer sendOnlyIfDelta;
    private boolean workaroundsOnlySliders;

    public DeviceSave getDeviceSave(String serialNum) {
        return devices.get(serialNum);
    }

    public void createSaveForNewDevice(String serialNum, DeviceType dt) {
        devices.put(serialNum, new DeviceSave(dt).generateDefaultDisplayName(this));
    }

    public boolean doesDeviceDisplayNameExist(String displayName) {
        if (displayName == null)
            throw new IllegalArgumentException("cannot have null displayName");
        return devices.values().stream().anyMatch(device -> displayName.equals(device.getDisplayName()));
    }

    public void setPreventSliderTwitchDelay(Integer preventSliderTwitchDelay) {
        this.preventSliderTwitchDelay = preventSliderTwitchDelay == null || preventSliderTwitchDelay == 0 ? null : preventSliderTwitchDelay;
    }

    public void setSliderRollingAverage(Integer sliderRollingAverage) {
        this.sliderRollingAverage = sliderRollingAverage == null || sliderRollingAverage == 0 ? null : sliderRollingAverage;
    }

    public void setSendOnlyIfDelta(Integer sendOnlyIfDelta) {
        this.sendOnlyIfDelta = sendOnlyIfDelta == null || sendOnlyIfDelta == 0 ? null : sendOnlyIfDelta;
    }
}
