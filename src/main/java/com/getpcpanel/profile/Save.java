package com.getpcpanel.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.getpcpanel.template.TemplateSaveMigration;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.device.descriptor.DeviceDescriptor;
import com.getpcpanel.integration.homeassistant.dto.HomeAssistantServer;
import com.getpcpanel.integration.discord.dto.DiscordAuth;
import com.getpcpanel.integration.discord.dto.DiscordSeenUser;
import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.integration.volume.FocusVolumeOverride;
import com.getpcpanel.profile.dto.CurveDefinition;
import com.getpcpanel.profile.dto.NotificationAlert;
import com.getpcpanel.integration.mqtt.dto.MqttSettings;
import com.getpcpanel.integration.osc.dto.OSCConnectionInfo;
import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.integration.volume.overlay.OverlayPosition;
import com.getpcpanel.integration.wavelink.dto.WaveLinkSettings;

import com.sun.jna.Platform;

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
    /** Diameter of the overlay bar's knob, in pixels; 0 is no knob. */
    public static final int DEFAULT_OVERLAY_KNOB_SIZE = 24;
    public static final int DEFAULT_OVERLAY_PADDING = 10;
    /** Width of the line that marks the soft-takeover point on the overlay's bar, in pixels; 0 is no line. */
    public static final int DEFAULT_OVERLAY_TAKEOVER_MARKER_WIDTH = 3;
    public static final String DEFAULT_OVERLAY_TAKEOVER_MARKER_COLOR = "rgb(255, 128, 0)";
    /** Height of that line, in pixels: the knob's default size. */
    public static final int DEFAULT_OVERLAY_TAKEOVER_MARKER_HEIGHT = 24;
    public static final int DEFAULT_OVERLAY_TEXT_SIZE = 14;
    public static final int DEFAULT_OVERLAY_ICON_SIZE = 32;
    public static final int DEFAULT_OVERLAY_ELEMENT_GAP = 10;
    public static final int DEFAULT_OVERLAY_WIDTH = 340;
    public static final int DEFAULT_OVERLAY_CONTENT_PADDING = 10;
    private static final OverlayPosition DEFAULT_OVERLAY_POSITION = OverlayPosition.topLeft;
    private Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    /** Which template syntax the saved text fields are written for; see {@code TemplateSaveMigration}. */
    private int templateVersion = TemplateSaveMigration.CURRENT_VERSION;
    private boolean mainUIIcons = true;
    /** Open the UI in the default browser every time the app starts. Default off — PCPanel runs in the
     *  tray and the UI is opened on demand; a first run and an installer launch open it regardless. */
    private boolean openBrowserOnStartup;
    private boolean startupVersionCheck = true;
    /** Include pre-release (snapshot) builds when checking for updates. Explicit choice — the running
     *  build's own snapshot-ness no longer decides this. Only meaningful when startupVersionCheck is on. */
    private boolean checkForPreReleases;
    /** Windows only: on startup, download and silently install a newer version (of the chosen type),
     *  then restart. Off by default; only meaningful when startupVersionCheck is on. */
    private boolean autoUpdate;
    private boolean forceVolume; // Linux only
    /** An app that starts playing sound takes the level of the App-volume control (or focus dial) that names it.
     *  Null is the platform default: on for Linux, off for Windows, which restores each app's own volume. */
    @Nullable private Boolean newAppsAtDialLevel;
    /** Apps for which {@link #newAppsAtDialLevel} is inverted. */
    @Nullable private List<String> newAppsAtDialLevelExceptions;
    /** Switch the panel lights off when the PC locks / the monitors sleep / the PC suspends, and back
     *  on afterwards. Opt-out escape hatch for #145-class detection trouble; the lights-off on app
     *  shutdown is unaffected. */
    private boolean sleepDetectionEnabled = true;
    /** While the lights are off for a lock or screens off (not sleep), the music visualizer keeps showing on them. */
    private boolean visualizerWhileLocked;
    /** While the lights are off for a lock or screens off (not sleep), notification lights keep showing on them. */
    private boolean notificationLightsWhileLocked;
    private Long dblClickInterval = 500L;
    /** "No volume jumps" for knobs / sliders: after their target changed elsewhere, wait until the control reaches it. */
    private boolean softTakeoverKnobs;
    private boolean softTakeoverSliders;
    /** Play a short light show when a device connects. */
    private boolean startupAnimation;
    /** Notification lights; see {@link NotificationAlert}. */
    @Nullable private List<NotificationAlert> notificationAlerts;
    /** How long (ms) a button must stay down to run its hold actions instead of its press actions. */
    @Nullable private Long holdInterval;
    private boolean preventClickWhenDblClick = true;
    /** When set, focused-app volume does nothing for apps already controlled elsewhere (a per-app
     *  volume command on another control, or a Wave Link channel). */
    private boolean skipControlledFocusApps;
    /** Focus-volume redirection rules: when a source app has focus, the focus dial drives the rule's
     *  targets instead of (or alongside) the source. See {@link FocusVolumeOverride}. */
    @Nullable private List<FocusVolumeOverride> focusVolumeOverrides;
    /** The user's response-curve library. An entry carrying a built-in id retunes that built-in for every
     *  control referencing it; removing it restores the default. See {@link com.getpcpanel.commands.curve.Curves}. */
    @Nullable private List<CurveDefinition> curves;
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
    @Nullable private SonarSettings sonar;
    @Nullable private List<HomeAssistantServer> homeAssistantServers;
    /** Leading+trailing throttle window (ms) for analog Home Assistant sends; null/0 = disabled. */
    @Nullable private Integer homeAssistantDebounceMs;
    /** Discord integration config (client id/secret/enabled). User-editable; tokens live in {@link #discordAuth}. */
    @Nullable private DiscordSettings discord;
    /** Machine-managed Discord OAuth tokens — kept apart from {@link #discord} so a config save never clears them. */
    @Nullable private DiscordAuth discordAuth;
    /** Discord users seen in a voice channel, so the command editor can target a username while not in a call. */
    @Nullable private List<DiscordSeenUser> discordSeenUsers;

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
    /** Diameter of the knob on the overlay's bar, in pixels; 0 hides it. */
    @Nullable private Integer overlayKnobSize = DEFAULT_OVERLAY_KNOB_SIZE;
    /** Width of the line on the overlay's bar that marks where a control waiting for soft takeover takes over; 0 hides it. */
    @Nullable private Integer overlayTakeoverMarkerWidth = DEFAULT_OVERLAY_TAKEOVER_MARKER_WIDTH;
    @Nullable private String overlayTakeoverMarkerColor = DEFAULT_OVERLAY_TAKEOVER_MARKER_COLOR;
    @Nullable private Integer overlayTakeoverMarkerHeight = DEFAULT_OVERLAY_TAKEOVER_MARKER_HEIGHT;
    /** Corner rounding of that line, in pixels; 0 is square. */
    @Nullable private Integer overlayTakeoverMarkerRounding = 0;
    /** Whether the overlay names where a control waiting for soft takeover has to go ("move to 40% to take over"). */
    private boolean overlayTakeoverText = true;
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
    /** Show what a button action did ("Spotify · Muted", the new default device, the profile switched to). */
    private boolean overlayButtonFeedback = true;

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

    public int getOverlayKnobSize() {
        return overlayKnobSize == null ? DEFAULT_OVERLAY_KNOB_SIZE : overlayKnobSize;
    }

    public int getOverlayTakeoverMarkerWidth() {
        return overlayTakeoverMarkerWidth == null ? DEFAULT_OVERLAY_TAKEOVER_MARKER_WIDTH : overlayTakeoverMarkerWidth;
    }

    public int getOverlayTakeoverMarkerHeight() {
        return overlayTakeoverMarkerHeight == null ? DEFAULT_OVERLAY_TAKEOVER_MARKER_HEIGHT : overlayTakeoverMarkerHeight;
    }

    public int getOverlayTakeoverMarkerRounding() {
        return overlayTakeoverMarkerRounding == null ? 0 : overlayTakeoverMarkerRounding;
    }

    public String getOverlayTakeoverMarkerColor() {
        return overlayTakeoverMarkerColor == null || overlayTakeoverMarkerColor.isBlank() ? DEFAULT_OVERLAY_TAKEOVER_MARKER_COLOR : overlayTakeoverMarkerColor;
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
    public SonarSettings getSonar() {
        return Objects.requireNonNullElse(sonar, SonarSettings.DEFAULT);
    }

    @Nonnull
    public List<HomeAssistantServer> getHomeAssistantServers() {
        return Objects.requireNonNullElseGet(homeAssistantServers, List::of);
    }

    @Nonnull
    public DiscordSettings getDiscord() {
        return Objects.requireNonNullElse(discord, DiscordSettings.DEFAULT);
    }

    @Nonnull
    public List<DiscordSeenUser> getDiscordSeenUsers() {
        return Objects.requireNonNullElseGet(discordSeenUsers, List::of);
    }

    @Nonnull
    public List<FocusVolumeOverride> getFocusVolumeOverrides() {
        return Objects.requireNonNullElseGet(focusVolumeOverrides, List::of);
    }

    /** {@link #newAppsAtDialLevel} with the platform default filled in. Not a bean getter, so it is not serialised. */
    public boolean effectiveNewAppsAtDialLevel() {
        return newAppsAtDialLevel != null ? newAppsAtDialLevel : Platform.isLinux();
    }

    public long getHoldInterval() {
        return holdInterval == null ? 500L : holdInterval;
    }

    @Nonnull
    public List<String> getNewAppsAtDialLevelExceptions() {
        return Objects.requireNonNullElseGet(newAppsAtDialLevelExceptions, List::of);
    }

    @Nonnull
    public List<NotificationAlert> getNotificationAlerts() {
        return Objects.requireNonNullElseGet(notificationAlerts, List::of);
    }

    @Nonnull
    public List<CurveDefinition> getCurves() {
        return Objects.requireNonNullElseGet(curves, List::of);
    }
}
