package com.getpcpanel.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.profile.dto.MqttSettings;
import com.getpcpanel.profile.dto.OSCConnectionInfo;
import com.getpcpanel.profile.dto.OverlayPosition;
import com.getpcpanel.profile.dto.WaveLinkSettings;

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
    public static final int DEFAULT_OVERLAY_TEXT_SIZE = 14;
    public static final int DEFAULT_OVERLAY_ICON_SIZE = 32;
    public static final int DEFAULT_OVERLAY_ELEMENT_GAP = 10;
    public static final int DEFAULT_OVERLAY_WIDTH = 340;
    public static final int DEFAULT_OVERLAY_CONTENT_PADDING = 10;
    private static final OverlayPosition DEFAULT_OVERLAY_POSITION = OverlayPosition.topLeft;
    private Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    private boolean mainUIIcons;
    private boolean startupVersionCheck = true;
    private boolean forceVolume; // Linux only
    private Long dblClickInterval = 500L;
    private boolean preventClickWhenDblClick = true;
    /** When set, focused-app volume does nothing for apps already controlled elsewhere (a per-app
     *  volume command on another control, or a Wave Link channel). */
    private boolean skipControlledFocusApps;
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
    private boolean oscEnabled;
    private Integer oscListenPort;
    private List<OSCConnectionInfo> oscConnections;
    private MqttSettings mqtt;
    private WaveLinkSettings waveLink;
    @Nullable private List<HomeAssistantServer> homeAssistantServers;
    /** Leading+trailing throttle window (ms) for analog Home Assistant sends; null/0 = disabled. */
    @Nullable private Integer homeAssistantDebounceMs;

    // Overlay
    private boolean overlayEnabled;
    private boolean overlayUseLog;
    private boolean overlayShowNumber;
    /** Show the controlled-target's icon on the overlay (the bar is always shown). */
    private boolean overlayShowIcon = true;
    private String overlayBackgroundColor = DEFAULT_OVERLAY_BG_COLOR;
    private String overlayTextColor = DEFAULT_OVERLAY_TEXT_COLOR;
    private String overlayBarColor = DEFAULT_OVERLAY_BAR_COLOR;
    private String overlayBarBackgroundColor = DEFAULT_OVERLAY_BAR_BACKGROUND_COLOR;
    private int overlayWindowCornerRounding;
    @Nullable private Integer overlayBarHeight = DEFAULT_OVERLAY_BAR_HEIGHT;
    @Nullable private Integer overlayBarCornerRounding = 0;
    @Nullable private OverlayPosition overlayPosition = DEFAULT_OVERLAY_POSITION;
    @Nullable private Integer overlayPadding = DEFAULT_OVERLAY_PADDING;
    /**
     * Show the controlled-target name (focused app / process / channel / device) on the overlay. This
     * also drives the layout: on → two rows ([icon] [name] [percent] over a full-width bar), off → a
     * compact single row ([icon] [bar] [percent]).
     */
    private boolean overlayShowAppName = true;
    @Nullable private Integer overlayTextSize = DEFAULT_OVERLAY_TEXT_SIZE;
    @Nullable private Integer overlayIconSize = DEFAULT_OVERLAY_ICON_SIZE;
    @Nullable private Integer overlayElementGap = DEFAULT_OVERLAY_ELEMENT_GAP;
    /** Overall overlay window width in px. */
    @Nullable private Integer overlayWidth = DEFAULT_OVERLAY_WIDTH;
    /** Inner padding between the overlay's edges and its content in px (shrinks the whole overlay). */
    @Nullable private Integer overlayContentPadding = DEFAULT_OVERLAY_CONTENT_PADDING;
    /** Use the control's current light colour as the bar colour (falls back to the bar colour). */
    private boolean overlayBarFollowsLight;
    /** Overlay text font family; null/blank = the default ("Segoe UI"). Must be a family the JVM has. */
    @Nullable private String overlayFontFamily;
    /** Render the overlay text bold. */
    private boolean overlayFontBold = true;

    public int getOverlayWidth() {
        return overlayWidth == null ? DEFAULT_OVERLAY_WIDTH : overlayWidth;
    }

    public int getOverlayContentPadding() {
        return overlayContentPadding == null ? DEFAULT_OVERLAY_CONTENT_PADDING : overlayContentPadding;
    }

    public int getOverlayTextSize() {
        return overlayTextSize == null ? DEFAULT_OVERLAY_TEXT_SIZE : overlayTextSize;
    }

    public int getOverlayIconSize() {
        return overlayIconSize == null ? DEFAULT_OVERLAY_ICON_SIZE : overlayIconSize;
    }

    public int getOverlayElementGap() {
        return overlayElementGap == null ? DEFAULT_OVERLAY_ELEMENT_GAP : overlayElementGap;
    }

    public DeviceSave getDeviceSave(String serialNum) {
        return devices.get(serialNum);
    }

    public void createSaveForNewDevice(String serialNum, DeviceDescriptor descriptor) {
        devices.put(serialNum, new DeviceSave(this, descriptor));
    }

    /** @deprecated use {@link #createSaveForNewDevice(String, DeviceDescriptor)}; kept as a shim during the device-layer transition. */
    @Deprecated
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

    @Nonnull
    public WaveLinkSettings getWaveLink() {
        return Objects.requireNonNullElse(waveLink, WaveLinkSettings.DEFAULT);
    }

    @Nonnull
    public MqttSettings getMqtt() {
        return Objects.requireNonNullElse(mqtt, MqttSettings.DEFAULT);
    }

    @Nonnull
    public List<HomeAssistantServer> getHomeAssistantServers() {
        return Objects.requireNonNullElseGet(homeAssistantServers, List::of);
    }
}
