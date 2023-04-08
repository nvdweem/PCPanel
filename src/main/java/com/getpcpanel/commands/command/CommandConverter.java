package com.getpcpanel.commands.command;

import static com.getpcpanel.commands.command.CommandNoOp.NOOP;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.voicemeeter.Voicemeeter;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
public final class CommandConverter {
    private CommandConverter() {
    }

    public static Command convert(String[] data) {
        if (data == null || data.length < 1 || StringUtils.isBlank(data[0])) {
            return NOOP;
        }

        return switch (data[0]) {
            // Dials
            case "app_volume" -> {
                var device = data[3];
                var apps = StreamEx.of(data[1], data[2]).map(StringUtils::trimToNull).nonNull().toList();
                yield new CommandVolumeProcess(apps, device, false, false);
            }
            case "focus_volume" -> new CommandVolumeFocus(false);
            case "device_volume" -> new CommandVolumeDevice(data[1], false, false);
            case "obs_dial" -> new CommandObsSetSourceVolume(data[2], false);
            case "voicemeeter_dial" -> {
                if ("basic".equals(data[1])) {
                    yield new CommandVoiceMeeterBasic(Voicemeeter.ControlType.valueOf(data[2]), NumberUtils.toInt(data[3], 1), Voicemeeter.DialType.valueOf(data[4]), false);
                } else if ("advanced".equals(data[1])) {
                    var dt = Voicemeeter.DialControlMode.valueOf(data[3]);
                    yield new CommandVoiceMeeterAdvanced(data[2], dt, false);
                }
                yield NOOP;
            }

            // Buttons
            case "keystroke" -> {
                if (StringUtils.isBlank(data[1]))
                    yield NOOP;
                yield new CommandKeystroke(data[1]);
            }
            case "shortcut" -> new CommandShortcut(data[1]);
            case "media" -> CommandMedia.VolumeButton.tryValueOf(data[1]).map(v -> new CommandMedia(v, false)).map(Command.class::cast).orElse(NOOP);
            case "end_program" -> new CommandEndProgram(StringUtils.equals("specific", data[1]), data[2]);
            case "sound_device" -> new CommandVolumeDefaultDevice(data[1]);
            case "toggle_device" -> new CommandVolumeDefaultDeviceToggle(List.of(data[1].split("\\|")));
            case "mute_app" -> new CommandVolumeProcessMute(Set.of(data[1]), MuteType.valueOf(data[2]));
            case "mute_device" -> new CommandVolumeDeviceMute(data[1], MuteType.valueOf(data[2]));
            case "obs_button" -> {
                if ("set_scene".equals(data[1])) {
                    yield new CommandObsSetScene(data[2]);
                } else if ("mute_source".equals(data[1])) {
                    yield new CommandObsMuteSource(data[2], CommandObsMuteSource.MuteType.valueOf(data[3]));
                }
                yield NOOP;
            }
            case "voicemeeter_button" -> {
                if ("basic".equals(data[1])) {
                    yield new CommandVoiceMeeterBasicButton(Voicemeeter.ControlType.valueOf(data[2]), NumberUtils.toInt(data[3], 1), Voicemeeter.ButtonType.valueOf(data[4]));
                } else if ("advanced".equals(data[1])) {
                    var bt = Voicemeeter.ButtonControlMode.valueOf(data[3]);
                    yield new CommandVoiceMeeterAdvancedButton(data[2], bt, null);
                }
                yield NOOP;
            }
            case "profile" -> {
                if (data[1] == null)
                    yield NOOP;
                yield new CommandProfile(data[1]);
            }

            default -> NOOP;
        };
    }
}
