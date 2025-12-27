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
    public static final String DEFAULT_OVERLAY_BAR_COLOR = "rgb(0, 148, 197)";
    public static final String DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR = "rgb(249, 249, 249)";
    public static final int DEFAULT_OVERLAY_BAR_HEIGHT = 18;
    public static final int DEFAULT_OVERLAY_PADDING = 10;
    public static final String DEFAULT_SONAR_CORE_PROPS_PATH = "C:\\ProgramData\\SteelSeries\\SteelSeries Engine 3\\coreProps.json";
    public static final String DEFAULT_DISCORD_CLIENT_ID = "1454170387217780882";
    private static final OverlayPosition DEFAULT_OVERLAY_POSITION = OverlayPosition.topLeft;
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
    private boolean discordEnabled;
    private String discordIpcPath;
    private String discordClientId = DEFAULT_DISCORD_CLIENT_ID;
    private String discordMuteHotkey;
    private String discordDeafenHotkey;
    private String discordPttHotkey;
    private String sonarCorePropsPath = DEFAULT_SONAR_CORE_PROPS_PATH;
    @Nullable private Integer preventSliderTwitchDelay;
    @Nullable private Integer sliderRollingAverage;
    @Nullable private Integer sendOnlyIfDelta;
    private boolean workaroundsOnlySliders;
    private boolean debugMode;
    private Integer oscListenPort;
    private List<OSCConnectionInfo> oscConnections;
    private MqttSettings mqtt;

    // Overlay
    private boolean overlayEnabled;
    private boolean overlayUseLog;
    private boolean overlayShowNumber;
    private String overlayBackgroundColor = DEFAULT_OVERLAY_BG_COLOR;
    private String overlayTextColor = DEFAULT_OVERLAY_TEXT_COLOR;
    private String overlayBarColor = DEFAULT_OVERLAY_BAR_COLOR;
    private String overlayBarBackgroundColor = DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR;
    @Nullable private Integer overlayWindowCornerRounding = 0;
    @Nullable private Integer overlayBarHeight = DEFAULT_OVERLAY_BAR_HEIGHT;
    @Nullable private Integer overlayBarCornerRounding = 0;
    @Nullable private OverlayPosition overlayPosition = DEFAULT_OVERLAY_POSITION;
    @Nullable private Integer overlayPadding = DEFAULT_OVERLAY_PADDING;

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

    public int getOverlayWindowCornerRounding() {
        return overlayWindowCornerRounding == null ? 0 : overlayWindowCornerRounding;
    }

    public int getOverlayBarCornerRounding() {
        return overlayBarCornerRounding == null ? 0 : overlayBarCornerRounding;
    }

    public OverlayPosition getOverlayPosition() {
        return overlayPosition == null ? DEFAULT_OVERLAY_POSITION : overlayPosition;
    }

    public int getOverlayPadding() {
        return overlayPadding == null ? DEFAULT_OVERLAY_PADDING : overlayPadding;
    }

    public int getOverlayBarHeight() {
        return overlayBarHeight == null ? DEFAULT_OVERLAY_BAR_HEIGHT : overlayBarHeight;
    }
}
