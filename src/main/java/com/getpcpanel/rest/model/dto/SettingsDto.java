package com.getpcpanel.rest.model.dto;

import java.util.List;

import javax.annotation.Nullable;

import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.dto.OSCConnectionInfo;
import com.getpcpanel.profile.dto.OverlayPosition;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SettingsDto {
    // General
    private boolean mainUIIcons;
    private boolean startupVersionCheck;
    private boolean forceVolume;
    private Long dblClickInterval;
    private boolean preventClickWhenDblClick;
    @Nullable private Integer preventSliderTwitchDelay;
    @Nullable private Integer sliderRollingAverage;
    @Nullable private Integer sendOnlyIfDelta;
    private boolean workaroundsOnlySliders;

    // OBS
    private boolean obsEnabled;
    private String obsAddress;
    private String obsPort;
    private String obsPassword;

    // VoiceMeeter
    private boolean voicemeeterEnabled;
    private String voicemeeterPath;

    // OSC
    private Integer oscListenPort;
    private List<OSCConnectionInfo> oscConnections;

    // Overlay
    private boolean overlayEnabled;
    private boolean overlayUseLog;
    private boolean overlayShowNumber;
    private String overlayBackgroundColor;
    private String overlayTextColor;
    private String overlayBarColor;
    private String overlayBarBackgroundColor;
    @Nullable private Integer overlayWindowCornerRounding;
    @Nullable private Integer overlayBarHeight;
    @Nullable private Integer overlayBarCornerRounding;
    @Nullable private OverlayPosition overlayPosition;
    @Nullable private Integer overlayPadding;

    public static SettingsDto from(Save save) {
        var dto = new SettingsDto();
        dto.mainUIIcons = save.isMainUIIcons();
        dto.startupVersionCheck = save.isStartupVersionCheck();
        dto.forceVolume = save.isForceVolume();
        dto.dblClickInterval = save.getDblClickInterval();
        dto.preventClickWhenDblClick = save.isPreventClickWhenDblClick();
        dto.preventSliderTwitchDelay = save.getPreventSliderTwitchDelay();
        dto.sliderRollingAverage = save.getSliderRollingAverage();
        dto.sendOnlyIfDelta = save.getSendOnlyIfDelta();
        dto.workaroundsOnlySliders = save.isWorkaroundsOnlySliders();
        dto.obsEnabled = save.isObsEnabled();
        dto.obsAddress = save.getObsAddress();
        dto.obsPort = save.getObsPort();
        dto.obsPassword = save.getObsPassword();
        dto.voicemeeterEnabled = save.isVoicemeeterEnabled();
        dto.voicemeeterPath = save.getVoicemeeterPath();
        dto.oscListenPort = save.getOscListenPort();
        dto.oscConnections = save.getOscConnections();
        dto.overlayEnabled = save.isOverlayEnabled();
        dto.overlayUseLog = save.isOverlayUseLog();
        dto.overlayShowNumber = save.isOverlayShowNumber();
        dto.overlayBackgroundColor = save.getOverlayBackgroundColor();
        dto.overlayTextColor = save.getOverlayTextColor();
        dto.overlayBarColor = save.getOverlayBarColor();
        dto.overlayBarBackgroundColor = save.getOverlayBarBackgroundColor();
        dto.overlayWindowCornerRounding = save.getOverlayWindowCornerRounding();
        dto.overlayBarHeight = save.getOverlayBarHeight();
        dto.overlayBarCornerRounding = save.getOverlayBarCornerRounding();
        dto.overlayPosition = save.getOverlayPosition();
        dto.overlayPadding = save.getOverlayPadding();
        return dto;
    }

    public void applyTo(Save save) {
        save.setMainUIIcons(mainUIIcons);
        save.setStartupVersionCheck(startupVersionCheck);
        save.setForceVolume(forceVolume);
        save.setDblClickInterval(dblClickInterval);
        save.setPreventClickWhenDblClick(preventClickWhenDblClick);
        save.setPreventSliderTwitchDelay(preventSliderTwitchDelay);
        save.setSliderRollingAverage(sliderRollingAverage);
        save.setSendOnlyIfDelta(sendOnlyIfDelta);
        save.setWorkaroundsOnlySliders(workaroundsOnlySliders);
        save.setObsEnabled(obsEnabled);
        save.setObsAddress(obsAddress);
        save.setObsPort(obsPort);
        save.setObsPassword(obsPassword);
        save.setVoicemeeterEnabled(voicemeeterEnabled);
        save.setVoicemeeterPath(voicemeeterPath);
        save.setOscListenPort(oscListenPort);
        save.setOscConnections(oscConnections);
        save.setOverlayEnabled(overlayEnabled);
        save.setOverlayUseLog(overlayUseLog);
        save.setOverlayShowNumber(overlayShowNumber);
        save.setOverlayBackgroundColor(overlayBackgroundColor);
        save.setOverlayTextColor(overlayTextColor);
        save.setOverlayBarColor(overlayBarColor);
        save.setOverlayBarBackgroundColor(overlayBarBackgroundColor);
        save.setOverlayWindowCornerRounding(overlayWindowCornerRounding);
        save.setOverlayBarHeight(overlayBarHeight);
        save.setOverlayBarCornerRounding(overlayBarCornerRounding);
        save.setOverlayPosition(overlayPosition);
        save.setOverlayPadding(overlayPadding);
    }
}
