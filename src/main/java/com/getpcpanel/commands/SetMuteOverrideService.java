package com.getpcpanel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.cpp.AudioDeviceEvent;
import com.getpcpanel.cpp.AudioSessionEvent;
import com.getpcpanel.cpp.EventType;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SingleKnobLightingConfig;
import com.getpcpanel.profile.SingleSliderLabelLightingConfig;
import com.getpcpanel.profile.SingleSliderLightingConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

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

    @EventListener(DeviceScanner.DeviceConnectedEvent.class)
    public void triggerAll() {
        for (var device : sndCtrl.getDevices()) {
            onAudioDevice(new AudioDeviceEvent(device, EventType.CHANGED));
        }
        for (var sess : sndCtrl.getAllSessions()) {
            onAudioSession(new AudioSessionEvent(sess, EventType.CHANGED));
        }
    }

    @EventListener
    public void onAudioSession(AudioSessionEvent event) {
        handleEvent(
                cmd -> cmd instanceof CommandVolumeProcess vd && vd.getProcessName().contains(event.session().executable().getName()),
                event.session().muted());
    }

    @EventListener
    public void onAudioDevice(AudioDeviceEvent event) {
        handleEvent(
                cmd -> cmd instanceof CommandVolumeDevice vd && sndCtrl.defaultDeviceOnEmpty(vd.getDeviceId()).equals(event.device().id()),
                event.device().muted());
    }

    public void handleEvent(Predicate<Command> isApplicable, boolean isMuted) {
        for (var idDeviceSave : saveService.get().getDevices().entrySet()) {
            var deviceSave = idDeviceSave.getValue();
            if (deviceSave.getLightingConfig().getLightingMode() != LightingConfig.LightingMode.CUSTOM) {
                continue;
            }

            var mayBeChanged = false;
            for (var dlc : tryGetAllDeviceLightingCapable(deviceSave)) {
                if (isApplicable.test(dlc.cmd)) {
                    if (isMuted) {
                        dlc.toMuteColor.run();
                    } else {
                        dlc.toOriginal.run();
                    }
                    mayBeChanged = true;
                }
            }
            if (mayBeChanged) {
                devices.getDevice(idDeviceSave.getKey()).setLighting(deviceSave.getLightingConfig(), true);
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
                var muteOverrideColor = oLightConfig.getKnobConfigs()[idx].getMuteOverrideColor();
                if (StringUtils.isNotEmpty(muteOverrideColor)) {
                    Runnable toOriginal = () -> tLightConfig.getKnobConfigs()[idx] = oLightConfig.getKnobConfigs()[idx];
                    Runnable toMute = () -> tLightConfig.getKnobConfigs()[idx] = new SingleKnobLightingConfig().setMode(SingleKnobLightingConfig.SINGLE_KNOB_MODE.STATIC)
                                                                                                               .setColor1(muteOverrideColor)
                                                                                                               .setMuteOverrideColor(muteOverrideColor);
                    result.add(new DeviceLightingCapable(command, toOriginal, toMute));
                }
            } else { // It's a slider with label
                var slider = idx - knobLength;
                var sliderOverride = oLightConfig.getSliderConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNotBlank(sliderOverride)) {
                    Runnable toOriginal = () -> tLightConfig.getSliderConfigs()[slider] = oLightConfig.getSliderConfigs()[slider];
                    Runnable toMute = () -> tLightConfig.getSliderConfigs()[slider] = new SingleSliderLightingConfig().setMode(SingleSliderLightingConfig.SINGLE_SLIDER_MODE.STATIC)
                                                                                                                      .setColor1(sliderOverride)
                                                                                                                      .setMuteOverrideColor(sliderOverride);
                    result.add(new DeviceLightingCapable(command, toOriginal, toMute)); // Slider
                }

                var labelOverride = oLightConfig.getSliderLabelConfigs()[slider].getMuteOverrideColor();
                if (StringUtils.isNotBlank(labelOverride)) {
                    Runnable toOriginal = () -> tLightConfig.getSliderLabelConfigs()[slider] = oLightConfig.getSliderLabelConfigs()[slider];
                    Runnable toMute = () -> tLightConfig.getSliderLabelConfigs()[slider] = new SingleSliderLabelLightingConfig().setMode(SingleSliderLabelLightingConfig.SINGLE_SLIDER_LABEL_MODE.STATIC)
                                                                                                                                .setColor(labelOverride)
                                                                                                                                .setMuteOverrideColor(labelOverride);
                    result.add(new DeviceLightingCapable(command, toOriginal, toMute)); // Slider label
                }
            }
        }

        return result;
    }

    record DeviceLightingCapable(Command cmd, Runnable toOriginal, Runnable toMuteColor) {
    }
}
