package com.getpcpanel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

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
import com.getpcpanel.util.coloroverride.ColorOverrideHolder;
import com.getpcpanel.util.coloroverride.IOverrideColorProvider;
import com.getpcpanel.util.coloroverride.IOverrideColorProviderProvider;
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
@ApplicationScoped
@Priority(0)
public class SetMuteOverrideService implements IOverrideColorProviderProvider {
    private static final Pattern voiceMeeterPattern = Pattern.compile("VoiceMeeter: (Input|Output) (\\d+), (.*)"); // 1: In/Out, 2: Idx, 3: ButtonType
    @Inject
    DeviceHolder devices;
    @Inject
    ISndCtrl sndCtrl;
    @Inject
    SaveService saveService;
    @Inject
    OBS obs;
    private final ColorOverrideHolder colorOverrideHolder = new ColorOverrideHolder();

        public void triggerAll() {
        colorOverrideHolder.clearAllOverrides();
        for (var device : sndCtrl.devices()) {
            onAudioDevice(new AudioDeviceEvent(device, EventType.CHANGED));
        }
        for (var sess : sndCtrl.getAllSessions()) {
            onAudioSession(new AudioSessionEvent(sess, EventType.CHANGED));
        }
        updateObs(new OBSConnectEvent(obs.isConnected()));
    }

        public void updateObs(@Observes OBSConnectEvent event) {
        if (!event.connected()) {
            return;
        }

        EntryStream.of(obs.getSourcesWithMuteState()).mapKeyValue(OBSMuteEvent::new).forEach(this::onObsSource);
    }

        public void onObsSource(@Observes OBSMuteEvent event) {
        var lcName = event.input();
        handleEvent(
                dlc -> isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandObsSetSourceVolume.class).flatMap(ms -> StreamEx.of(ms.getSourceName()).findFirst(n -> n.contains(lcName))).isPresent(),
                event.muted());
    }

        public void onVoiceMeeterSource(@Observes VoiceMeeterMuteEvent event) {
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
                            return StringUtils.startsWithIgnoreCase(vmAdv.getFullParam(), type.getName() + "[" + idx + "]");
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

        public void onAudioSession(@Observes AudioSessionEvent event) {
        var lcName = StringUtils.lowerCase(event.session().executable().getName().toLowerCase());
        handleEvent(
                dlc -> isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandVolumeProcess.class).flatMap(vd -> StreamEx.of(vd.getProcessName()).map(String::toLowerCase).findFirst(n -> n.contains(lcName))).isPresent(),
                event.session().muted());
    }

        public void onAudioDevice(@Observes AudioDeviceEvent event) {
        handleEvent(
                dlc -> isDevice(event, dlc) || (isFollow(dlc) &&
                        dlc.cmd.getCommand(CommandVolumeDevice.class).filter(vd -> sndCtrl.defaultDeviceOnEmpty(vd.getDeviceId()).equals(event.device().id())).isPresent()),
                event.device().muted());
    }

    private boolean isFollow(DeviceLightingCapable dlc) {
        return ILightingDialogMuteOverrideHelper.FOLLOW_PROCESS.equals(dlc.deviceOrFollow);
    }

    private boolean isDevice(AudioDeviceEvent event, DeviceLightingCapable dlc) {
        return StringUtils.containsIgnoreCase(event.device().getName(), dlc.deviceOrFollow);
    }

    public void handleEvent(Predicate<DeviceLightingCapable> isApplicable, boolean isMuted) {
        for (var idDeviceSave : saveService.get().devices().entrySet()) {
            var deviceOpt = devices.getDevice(idDeviceSave.getKey());
            if (deviceOpt.isEmpty()) {
                log.debug("Device {} not connected", idDeviceSave.getKey());
                continue;
            }
            var device = deviceOpt.get();
            var deviceSave = idDeviceSave.getValue();
            var profile = deviceSave.ensureCurrentProfile(device.deviceType());
            var mayBeChangedLC = device.lightingConfig();
            if (mayBeChangedLC.lightingMode() != LightingConfig.LightingMode.CUSTOM) {
                continue;
            }

            var mayBeChanged = false;
            for (var dlc : tryGetAllDeviceLightingCapable(device.getSerialNumber(), mayBeChangedLC, profile)) {
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

    private List<DeviceLightingCapable> tryGetAllDeviceLightingCapable(String deviceSerial, LightingConfig mayBeChangedLC, Profile profile) {
        try {
            return getAllDeviceLightingCapable(deviceSerial, mayBeChangedLC, profile);
        } catch (Exception e) {
            log.error("Unable to get device lighting capable", e);
            return List.of();
        }
    }

    private List<DeviceLightingCapable> getAllDeviceLightingCapable(String deviceSerial, LightingConfig mayBeChangedLC, Profile profile) {
        var result = new ArrayList<DeviceLightingCapable>();

        var oLightConfig = profile.lightingConfig();
        var knobLength = mayBeChangedLC.knobConfigs().length;
        for (var idxCommand : profile.getDialData().entrySet()) {
            var idx = idxCommand.getKey();
            var command = idxCommand.getValue();
            if (idx < knobLength) {
                // It's a knob
                var deviceOrFollow = oLightConfig.knobConfigs()[idx].getMuteOverrideDeviceOrFollow();
                var muteOverrideColor = oLightConfig.knobConfigs()[idx].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(deviceOrFollow, muteOverrideColor)) {
                    Runnable toOriginal = () -> colorOverrideHolder.setDialOverride(deviceSerial, idx, null);
                    Runnable toMute = () -> colorOverrideHolder.setDialOverride(deviceSerial, idx, new SingleKnobLightingConfig().setMode(SingleKnobLightingConfig.SINGLE_KNOB_MODE.STATIC)
                                                                                                                                 .setColor1(muteOverrideColor)
                                                                                                                                 .setMuteOverrideDeviceOrFollow(deviceOrFollow)
                                                                                                                                 .setMuteOverrideColor(muteOverrideColor));
                    result.add(new DeviceLightingCapable(deviceOrFollow, command, toOriginal, toMute));
                }
            } else { // It's a slider with label
                var slider = idx - knobLength;
                var sliderDeviceOrFollow = oLightConfig.sliderConfigs()[slider].getMuteOverrideDeviceOrFollow();
                var sliderOverride = oLightConfig.sliderConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(sliderDeviceOrFollow, sliderOverride)) {
                    Runnable toOriginal = () -> colorOverrideHolder.setSliderOverride(deviceSerial, slider, null);
                    Runnable toMute = () -> colorOverrideHolder.setSliderOverride(deviceSerial, slider, new SingleSliderLightingConfig().setMode(SingleSliderLightingConfig.SINGLE_SLIDER_MODE.STATIC)
                                                                                                                                        .setColor1(sliderOverride)
                                                                                                                                        .setMuteOverrideDeviceOrFollow(sliderDeviceOrFollow)
                                                                                                                                        .setMuteOverrideColor(sliderOverride));
                    result.add(new DeviceLightingCapable(sliderDeviceOrFollow, command, toOriginal, toMute)); // Slider
                }

                var labelDeviceOrFollow = oLightConfig.sliderLabelConfigs()[slider].getMuteOverrideDeviceOrFollow();
                var labelOverride = oLightConfig.sliderLabelConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(labelDeviceOrFollow, labelOverride)) {
                    Runnable toOriginal = () -> colorOverrideHolder.setSliderLabelOverride(deviceSerial, slider, null);
                    Runnable toMute = () -> colorOverrideHolder.setSliderLabelOverride(deviceSerial, slider, new SingleSliderLabelLightingConfig().setMode(SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.STATIC)
                                                                                                                                                  .setColor(labelOverride)
                                                                                                                                                  .setMuteOverrideDeviceOrFollow(labelDeviceOrFollow)
                                                                                                                                                  .setMuteOverrideColor(labelOverride));
                    result.add(new DeviceLightingCapable(labelDeviceOrFollow, command, toOriginal, toMute)); // Slider label
                }
            }
        }

        return result;
    }

    @Override
    public IOverrideColorProvider getOverrideColorProvider() {
        return colorOverrideHolder;
    }

    record DeviceLightingCapable(@Nullable String deviceOrFollow, Commands cmd, Runnable toOriginal, Runnable toMuteColor) {
    }
}
