package com.getpcpanel.hid;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.Main;
import com.getpcpanel.commands.CommandDispatcher;
import com.getpcpanel.commands.KeyMacro;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandEndProgram;
import com.getpcpanel.commands.command.CommandMedia;
import com.getpcpanel.commands.command.CommandObsMuteSource;
import com.getpcpanel.commands.command.CommandObsSetScene;
import com.getpcpanel.commands.command.CommandObsSetSource;
import com.getpcpanel.commands.command.CommandShortcut;
import com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced;
import com.getpcpanel.commands.command.CommandVoiceMeeterBasic;
import com.getpcpanel.commands.command.CommandVolumeDefaultDevice;
import com.getpcpanel.commands.command.CommandVolumeDevice;
import com.getpcpanel.commands.command.CommandVolumeDeviceMute;
import com.getpcpanel.commands.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.CommandVolumeProcess;
import com.getpcpanel.commands.command.CommandVolumeProcessMute;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.Save;
import com.getpcpanel.util.Util;
import com.getpcpanel.voicemeeter.Voicemeeter;

import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public final class InputInterpreter {

    private InputInterpreter() {
    }

    public static void onKnobRotate(String serialNum, int knob, int value) {
        var device = Main.devices.get(serialNum);
        if (device == null)
            return;
        if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
            value = Util.map(value, 0, 255, 0, 100);
        Main.devices.get(serialNum).setKnobRotation(knob, value);
        var settings = Save.getDeviceSave(serialNum).getKnobSettings()[knob];
        if (settings != null) {
            if (settings.isLogarithmic())
                value = log(value);
            value = Util.map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
        }
        doDialAction(serialNum, knob, value);
    }

    public static void onButtonPress(String serialNum, int knob, boolean pressed) throws IOException {
        Main.devices.get(serialNum).setButtonPressed(knob, pressed);
        if (pressed)
            doClickAction(serialNum, knob);
    }

    private static void doDialAction(String serialNum, int knob, int v) {
        var data = Save.getDeviceSave(serialNum).dialData[knob];
        if (data == null || data[0] == null)
            return;

        var cmds = new ArrayList<Command>(2);
        switch (data[0]) {
            case "app_volume" -> {
                var device = data[3];
                StreamEx.of(data[1], data[2]).map(StringUtils::trimToNull).nonNull().map(val -> new CommandVolumeProcess(serialNum, knob, val, device, v)).into(cmds);
            }
            case "focus_volume" -> cmds.add(new CommandVolumeFocus(serialNum, knob, v));
            case "device_volume" -> cmds.add(new CommandVolumeDevice(serialNum, knob, data[1], v));
            case "obs_dial" -> cmds.add(new CommandObsSetSource(serialNum, knob, data[2], v));
            case "voicemeeter_dial" -> {
                if (!Voicemeeter.login())
                    return;
                if ("basic".equals(data[1])) {
                    cmds.add(new CommandVoiceMeeterBasic(serialNum, knob, Voicemeeter.ControlType.valueOf(data[2]), NumberUtils.toInt(data[3], 1), Voicemeeter.DialType.valueOf(data[4]), v));
                } else if ("advanced".equals(data[1])) {
                    var dt = Voicemeeter.DialControlMode.valueOf(data[3]);
                    cmds.add(new CommandVoiceMeeterAdvanced(serialNum, knob, data[2], dt, v));
                }
            }
        }
        CommandDispatcher.pushVolumeChange(serialNum, knob, cmds);
    }

    private static void doClickAction(String serialNum, int knob) {
        var data = Save.getDeviceSave(serialNum).buttonData[knob];
        if (data == null || data[0] == null)
            return;
        switch (data[0]) {
            case "keystroke" -> {
                if (StringUtils.isBlank(data[1]))
                    return;
                KeyMacro.executeKeyStroke(data[1]);
            }
            case "shortcut" -> CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandShortcut(serialNum, knob, data[1]));
            case "media" -> CommandMedia.VolumeButton.tryValueOf(data[1]).ifPresent(v ->
                    CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandMedia(serialNum, knob, v))
            );
            case "end_program" -> CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandEndProgram(serialNum, knob, StringUtils.equals("specific", data[1]), data[2]));
            case "sound_device" -> CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandVolumeDefaultDevice(serialNum, knob, data[1]));
            case "toggle_device" -> {
                var deviceArray = data[1].split("\\|");
                if (deviceArray.length == 0)
                    return;
                var index = 0;
                try {
                    index = Integer.parseInt(data[2]);
                } catch (Exception e) {
                    log.trace("Unable to parse {}", data[2], e);
                }
                if (index >= deviceArray.length)
                    index = 0;
                var device = deviceArray[index];
                CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandVolumeDefaultDevice(serialNum, knob, device));
                data[2] = String.valueOf(index + 1);
            }
            case "mute_app" -> CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandVolumeProcessMute(serialNum, knob, data[1], MuteType.valueOf(data[2])));
            case "mute_device" -> CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandVolumeDeviceMute(serialNum, knob, data[1], MuteType.valueOf(data[2])));
            case "obs_button" -> {
                if ("set_scene".equals(data[1])) {
                    CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandObsSetScene(serialNum, knob, data[2]));
                } else if ("mute_source".equals(data[1])) {
                    CommandDispatcher.pushVolumeChange(serialNum, knob, new CommandObsMuteSource(serialNum, knob, data[2], CommandObsMuteSource.MuteType.valueOf(data[3])));
                }
            }
            case "voicemeeter_button" -> {
                if (!Voicemeeter.login())
                    return;
                if ("basic".equals(data[1])) {
                    Voicemeeter.controlButton(Voicemeeter.ControlType.valueOf(data[2]), NumberUtils.toInt(data[3], 1), Voicemeeter.ButtonType.valueOf(data[4]));
                } else if ("advanced".equals(data[1])) {
                    var bt = Voicemeeter.ButtonControlMode.valueOf(data[3]);
                    Voicemeeter.controlButton(data[2], bt);
                }
            }
            case "profile" -> {
                if (data[1] == null)
                    return;
                Platform.runLater(() -> Main.devices.get(serialNum).setProfile(data[1]));
            }
        }
    }

    @SuppressWarnings("NumericCastThatLosesPrecision")
    private static int log(int x) {
        var cons = 21.6679065336D;
        var ans = Math.pow(Math.E, x / cons) - 1.0D;
        return (int) Math.round(ans);
    }
}
