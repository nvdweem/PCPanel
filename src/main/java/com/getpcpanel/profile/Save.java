package com.getpcpanel.profile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.ui.OverlayPosition;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
public class Save {
    public static final String DEFAULT_OVERLAY_BG_COLOR = "rgba(255, 255, 255, 0.5)";
    public static final String DEFAULT_OVERLAY_TEXT_COLOR = "rgba(0, 0, 0, 1)";
    private Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    private boolean mainUIIcons;
    private boolean startupVersionCheck = true;
    private Long dblClickInterval = 500L;
    private boolean preventClickWhenDblClick = true;
    private boolean obsEnabled;
    private String obsAddress = "localhost";
    private String obsPort = "4455";
    private String obsPassword;
    private boolean voicemeeterEnabled;
    private String voicemeeterPath = "C:\\Program Files (x86)\\VB\\Voicemeeter";
    @Nullable private Integer preventSliderTwitchDelay;
    @Nullable private Integer sliderRollingAverage;
    @Nullable private Integer sendOnlyIfDelta;
    private boolean workaroundsOnlySliders;
    private Integer oscListenPort;
    private List<OSCConnectionInfo> oscConnections;

    // Overlay
    private boolean overlayEnabled;
    private boolean overlayUseLog;
    private boolean overlayShowNumber;
    private String overlayBackgroundColor = DEFAULT_OVERLAY_BG_COLOR;
    private String overlayTextColor = DEFAULT_OVERLAY_TEXT_COLOR;
    @Nullable private Integer overlayCornerRounding = 0;
    @Nullable private OverlayPosition overlayPosition = OverlayPosition.topLeft;
    @Nullable private Integer overlayPadding = 10;

    public DeviceSave getDeviceSave(String serialNum) {
        return devices.get(serialNum);
    }

    public void createSaveForNewDevice(String serialNum, DeviceType dt) {
        devices.put(serialNum, new DeviceSave(this, dt));
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

    public int getOverlayCornerRounding() {
        return overlayCornerRounding == null ? 0 : overlayCornerRounding;
    }

    public OverlayPosition getOverlayPosition() {
        return overlayPosition == null ? OverlayPosition.topLeft : overlayPosition;
    }

    public int getOverlayPadding() {
        return overlayPadding == null ? 10 : overlayPadding;
    }
}
