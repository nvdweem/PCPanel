package com.getpcpanel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandObsSetSourceVolume;
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
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;
import com.getpcpanel.ui.ILightingDialogMuteOverrideHelper;
import com.getpcpanel.voicemeeter.VoiceMeeterMuteEvent;

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
    private final DeviceHolder devices;
    private final ISndCtrl sndCtrl;
    private final SaveService saveService;
    private final OBS obs;

    @EventListener(DeviceScanner.DeviceConnectedEvent.class)
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
                dlc -> isFollow(dlc) && dlc.cmd instanceof CommandObsSetSourceVolume ms && StreamEx.of(ms.getSourceName()).findFirst(n -> n.contains(lcName)).isPresent(),
                event.muted());
    }

    @EventListener
    public void onVoiceMeeterSource(VoiceMeeterMuteEvent event) {
        var type = event.ct();
        var idx = event.idx();
        handleEvent(
                dlc -> {
                    if (isFollow(dlc)) {
                        if (dlc.cmd instanceof CommandVoiceMeeterBasic vmBasic) {
                            return vmBasic.getCt() == type && vmBasic.getIndex() == idx;
                        } else if (dlc.cmd instanceof CommandVoiceMeeterAdvanced vmAdv) {
                            return StringUtils.startsWithIgnoreCase(vmAdv.getFullParam(), type.name() + "[" + idx + "]");
                        }
                    }
                    return false;
                },
                event.muted());
    }

    @EventListener
    public void onAudioSession(AudioSessionEvent event) {
        var lcName = StringUtils.lowerCase(event.session().executable().getName().toLowerCase());
        handleEvent(
                dlc -> isFollow(dlc) && dlc.cmd instanceof CommandVolumeProcess vd && StreamEx.of(vd.getProcessName()).map(String::toLowerCase).findFirst(n -> n.contains(lcName)).isPresent(),
                event.session().muted());
    }

    @EventListener
    public void onAudioDevice(AudioDeviceEvent event) {
        handleEvent(
                dlc -> isDevice(event, dlc) || (isFollow(dlc) && dlc.cmd instanceof CommandVolumeDevice vd && sndCtrl.defaultDeviceOnEmpty(vd.getDeviceId()).equals(event.device().id())),
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
            var deviceSave = idDeviceSave.getValue();
            if (deviceSave.getLightingConfig().getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
                continue;
            }

            var mayBeChanged = false;
            for (var dlc : tryGetAllDeviceLightingCapable(deviceSave)) {
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
                var device = devices.getDevice(idDeviceSave.getKey());
                if (device == null) {
                    return;
                }
                device.setLighting(deviceSave.getLightingConfig(), true);
            }
        }
    }

    private List<DeviceLightingCapable> tryGetAllDeviceLightingCapable(DeviceSave deviceSave) {
        try {
            return getAllDeviceLightingCapable(deviceSave);
        } catch (Exception e) {
            log.error("Unable to get device lighting capable", e);
            return List.of();
        }
    }

    private List<DeviceLightingCapable> getAllDeviceLightingCapable(DeviceSave deviceSave) {
        var result = new ArrayList<DeviceLightingCapable>();

        var profile = deviceSave.getCurrentProfile();
        var tLightConfig = deviceSave.getLightingConfig();
        var oLightConfig = profile.getLightingConfig();
        var knobLength = deviceSave.getLightingConfig().getKnobConfigs().length;
        for (var idxCommand : profile.getDialData().entrySet()) {
            var idx = idxCommand.getKey();
            var command = idxCommand.getValue();
            if (idx < knobLength) {
                // It's a knob
                var deviceOrFollow = oLightConfig.getKnobConfigs()[idx].getMuteOverrideDeviceOrFollow();
                var muteOverrideColor = oLightConfig.getKnobConfigs()[idx].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(deviceOrFollow, muteOverrideColor)) {
                    Runnable toOriginal = () -> tLightConfig.getKnobConfigs()[idx] = oLightConfig.getKnobConfigs()[idx];
                    Runnable toMute = () -> tLightConfig.getKnobConfigs()[idx] = new SingleKnobLightingConfig().setMode(SingleKnobLightingConfig.SINGLE_KNOB_MODE.STATIC)
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
                    Runnable toOriginal = () -> tLightConfig.getSliderConfigs()[slider] = oLightConfig.getSliderConfigs()[slider];
                    Runnable toMute = () -> tLightConfig.getSliderConfigs()[slider] = new SingleSliderLightingConfig().setMode(SingleSliderLightingConfig.SINGLE_SLIDER_MODE.STATIC)
                                                                                                                      .setColor1(sliderOverride)
                                                                                                                      .setMuteOverrideDeviceOrFollow(sliderDeviceOrFollow)
                                                                                                                      .setMuteOverrideColor(sliderOverride);
                    result.add(new DeviceLightingCapable(sliderDeviceOrFollow, command, toOriginal, toMute)); // Slider
                }

                var labelDeviceOrFollow = oLightConfig.getSliderLabelConfigs()[slider].getMuteOverrideDeviceOrFollow();
                var labelOverride = oLightConfig.getSliderLabelConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNoneBlank(labelDeviceOrFollow, labelOverride)) {
                    Runnable toOriginal = () -> tLightConfig.getSliderLabelConfigs()[slider] = oLightConfig.getSliderLabelConfigs()[slider];
                    Runnable toMute = () -> tLightConfig.getSliderLabelConfigs()[slider] = new SingleSliderLabelLightingConfig().setMode(SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.STATIC)
                                                                                                                                .setColor(labelOverride)
                                                                                                                                .setMuteOverrideDeviceOrFollow(labelDeviceOrFollow)
                                                                                                                                .setMuteOverrideColor(labelOverride);
                    result.add(new DeviceLightingCapable(labelDeviceOrFollow, command, toOriginal, toMute)); // Slider label
                }
            }
        }

        return result;
    }

    record DeviceLightingCapable(String deviceOrFollow, Command cmd, Runnable toOriginal, Runnable toMuteColor) {
    }
}
