package com.getpcpanel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
import com.getpcpanel.commands.command.CommandVoiceMeeter;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioDeviceEvent;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.obs.OBSConnectEvent;
import com.getpcpanel.obs.OBSMuteEvent;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.ui.ILightingDialogMuteOverrideHelper;
import com.getpcpanel.ui.LightningChangedToDefaultEvent;
import com.getpcpanel.voicemeeter.VoiceMeeterMuteEvent;
import com.getpcpanel.voicemeeter.Voicemeeter.ButtonType;
import com.getpcpanel.voicemeeter.Voicemeeter.ControlType;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

/**
 * Triggers a color change when the device or application that is controlled by the dial/slider is muted/unmuted.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class SetMuteOverrideService {
    private static final Pattern voiceMeeterPattern = Pattern.compile("VoiceMeeter: (Input|Output) (\\d+), (.*)"); // 1: In/Out, 2: Idx, 3: ButtonType
    private final DeviceHolder devices;
    private final ISndCtrl sndCtrl;
    private final SaveService saveService;
    private final OBS obs;

    @EventListener({ DeviceScanner.DeviceConnectedEvent.class, LightningChangedToDefaultEvent.class })
    public void triggerAll() {
        for (var device : sndCtrl.getDevices()) {
            onAudioDevice(new AudioDeviceEvent(device, EventType.CHANGED));
        }
        for (var sess : sndCtrl.getAllSessions()) {
            onAudioSession(new AudioSessionEvent(sess, EventType.CHANGED));
        }
        updateObs(new OBSConnectEvent(obs.isConnected()));
    }

    @EventListener(OBSConnectEvent.class)
    public void updateObs(OBSConnectEvent event) {
        if (!event.connected()) {
            return;
        }

        EntryStream.of(obs.getSourcesWithMuteState()).mapKeyValue(OBSMuteEvent::new).forEach(this::onObsSource);
    }

    @EventListener
    public void onObsSource(OBSMuteEvent event) {
        var lcName = event.input();
        handleEvent(
                dlc -> isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandObsSetSourceVolume.class).flatMap(ms -> StreamEx.of(ms.getSourceName()).findFirst(n -> n.contains(lcName))).isPresent(),
                event.muted());
    }

    @EventListener
    public void onVoiceMeeterSource(VoiceMeeterMuteEvent event) {
        var type = event.ct();
        var idx = event.idx();
        var button = event.button();
        handleEvent(
                dlc -> {
                    if (isFollow(dlc)) {
                        var voiceMeeterCmd = dlc.cmd.getCommand(CommandVoiceMeeter.class).orElse(null);
                        if (voiceMeeterCmd instanceof CommandVoiceMeeterBasic vmBasic) {
                            return vmBasic.getCt() == type && vmBasic.getIndex() == idx;
                        } else if (voiceMeeterCmd instanceof CommandVoiceMeeterAdvanced vmAdv) {
                            return StringUtils.startsWithIgnoreCase(vmAdv.getFullParam(), type.name() + "[" + idx + "]");
                        }
                    } else if (StringUtils.isNotBlank(dlc.deviceOrFollow)) {
                        var matcher = voiceMeeterPattern.matcher(dlc.deviceOrFollow);
                        if (!matcher.matches()) {
                            return false;
                        }
                        var inOut = ControlType.fromDn(matcher.group(1));
                        var gIdx = NumberUtils.toInt(matcher.group(2), -1) - 1;
                        var bType = ButtonType.fromName(matcher.group(3));
                        return inOut == type && idx == gIdx && bType == button;
                    }
                    return false;
                },
                event.state());
    }

    @EventListener
    public void onAudioSession(AudioSessionEvent event) {
        var lcName = StringUtils.lowerCase(event.session().executable().getName().toLowerCase());
        handleEvent(
                dlc -> isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandVolumeProcess.class).flatMap(vd -> StreamEx.of(vd.getProcessName()).map(String::toLowerCase).findFirst(n -> n.contains(lcName))).isPresent(),
                event.session().muted());
    }

    @EventListener
    public void onAudioDevice(AudioDeviceEvent event) {
        handleEvent(
                dlc -> isDevice(event, dlc) || (isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandVolumeDevice.class).filter(vd -> sndCtrl.defaultDeviceOnEmpty(vd.getDeviceId()).equals(event.device().id())).isPresent()),
                event.device().muted());
    }

    private boolean isFollow(DeviceLightingCapable dlc) {
        return ILightingDialogMuteOverrideHelper.FOLLOW_PROCESS.equals(dlc.deviceOrFollow);
    }

    private boolean isDevice(AudioDeviceEvent event, DeviceLightingCapable dlc) {
        return StringUtils.containsIgnoreCase(event.device().name(), dlc.deviceOrFollow);
    }

    public void handleEvent(Predicate<DeviceLightingCapable> isApplicable, boolean isMuted) {
        for (var idDeviceSave : saveService.get().getDevices().entrySet()) {
            var deviceOpt = devices.getDevice(idDeviceSave.getKey());
            if (deviceOpt.isEmpty()) {
                log.debug("Device {} not connected", idDeviceSave.getKey());
                continue;
            }
            var device = deviceOpt.get();
            var deviceSave = idDeviceSave.getValue();
            var profile = deviceSave.ensureCurrentProfile(device.getDeviceType());
            var mayBeChangedLC = device.getLightingConfig();
            if (mayBeChangedLC.getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
                continue;
            }

            var mayBeChanged = false;
            for (var dlc : tryGetAllDeviceLightingCapable(mayBeChangedLC, profile)) {
                if (isApplicable.test(dlc)) {
                    if (isMuted) {
                        dlc.toMuteColor.run();
                    } else {
                        dlc.toOriginal.run();
                    }
                    mayBeChanged = true;
                }
            }
            if (mayBeChanged) {
                device.setLighting(mayBeChangedLC, true);
            }
        }
    }

    private List<DeviceLightingCapable> tryGetAllDeviceLightingCapable(LightingConfig mayBeChangedLC, Profile profile) {
        try {
            return getAllDeviceLightingCapable(mayBeChangedLC, profile);
        } catch (Exception e) {
            log.error("Unable to get device lighting capable", e);
            return List.of();
        }
    }

    private List<DeviceLightingCapable> getAllDeviceLightingCapable(LightingConfig mayBeChangedLC, Profile profile) {
        var result = new ArrayList<DeviceLightingCapable>();

        var oLightConfig = profile.getLightingConfig();
        var knobLength = mayBeChangedLC.getKnobConfigs().length;
        for (var idxCommand : profile.getDialData().entrySet()) {
            var idx = idxCommand.getKey();
            var command = idxCommand.getValue();
            if (idx < knobLength) {
                // It's a knob
                var deviceOrFollow = oLightConfig.getKnobConfigs()[idx].getMuteOverrideDeviceOrFollow();
                var muteOverrideColor = oLightConfig.getKnobConfigs()[idx].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(deviceOrFollow, muteOverrideColor)) {
                    Runnable toOriginal = () -> mayBeChangedLC.getKnobConfigs()[idx] = oLightConfig.getKnobConfigs()[idx];
                    Runnable toMute = () -> mayBeChangedLC.getKnobConfigs()[idx] = new SingleKnobLightingConfig().setMode(SingleKnobLightingConfig.SINGLE_KNOB_MODE.STATIC)
                                                                                                                 .setColor1(muteOverrideColor)
                                                                                                                 .setMuteOverrideDeviceOrFollow(deviceOrFollow)
                                                                                                                 .setMuteOverrideColor(muteOverrideColor);
                    result.add(new DeviceLightingCapable(deviceOrFollow, command, toOriginal, toMute));
                }
            } else { // It's a slider with label
                var slider = idx - knobLength;
                var sliderDeviceOrFollow = oLightConfig.getSliderConfigs()[slider].getMuteOverrideDeviceOrFollow();
                var sliderOverride = oLightConfig.getSliderConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(sliderDeviceOrFollow, sliderOverride)) {
                    Runnable toOriginal = () -> mayBeChangedLC.getSliderConfigs()[slider] = oLightConfig.getSliderConfigs()[slider];
                    Runnable toMute = () -> mayBeChangedLC.getSliderConfigs()[slider] = new SingleSliderLightingConfig().setMode(SingleSliderLightingConfig.SINGLE_SLIDER_MODE.STATIC)
                                                                                                                        .setColor1(sliderOverride)
                                                                                                                        .setMuteOverrideDeviceOrFollow(sliderDeviceOrFollow)
                                                                                                                        .setMuteOverrideColor(sliderOverride);
                    result.add(new DeviceLightingCapable(sliderDeviceOrFollow, command, toOriginal, toMute)); // Slider
                }

                var labelDeviceOrFollow = oLightConfig.getSliderLabelConfigs()[slider].getMuteOverrideDeviceOrFollow();
                var labelOverride = oLightConfig.getSliderLabelConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(labelDeviceOrFollow, labelOverride)) {
                    Runnable toOriginal = () -> mayBeChangedLC.getSliderLabelConfigs()[slider] = oLightConfig.getSliderLabelConfigs()[slider];
                    Runnable toMute = () -> mayBeChangedLC.getSliderLabelConfigs()[slider] = new SingleSliderLabelLightingConfig().setMode(
                                                                                                                                          SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.STATIC)
                                                                                                                                  .setColor(labelOverride)
                                                                                                                                  .setMuteOverrideDeviceOrFollow(labelDeviceOrFollow)
                                                                                                                                  .setMuteOverrideColor(labelOverride);
                    result.add(new DeviceLightingCapable(labelDeviceOrFollow, command, toOriginal, toMute)); // Slider label
                }
            }
        }

        return result;
    }

    record DeviceLightingCapable(@Nullable String deviceOrFollow, Commands cmd, Runnable toOriginal, Runnable toMuteColor) {
    }
}
