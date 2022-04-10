package hid;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.sun.glass.ui.Application;

import commands.KeyMacro;
import commands.MediaKeys;
import main.Device;
import main.DeviceType;
import main.Window;
import save.KnobSetting;
import save.Save;
import util.CommandHandler;
import util.Util;
import voicemeeter.Voicemeeter;
import voicemeeter.Voicemeeter.ButtonControlMode;
import voicemeeter.Voicemeeter.ButtonType;
import voicemeeter.Voicemeeter.ControlType;
import voicemeeter.Voicemeeter.DialControlMode;
import voicemeeter.Voicemeeter.DialType;

public class InputInterpreter {
    private static final Runtime rt = Runtime.getRuntime();

    public static void onKnobRotate(String serialNum, int knob, int value) {
        Device device = Window.devices.get(serialNum);
        if (device == null)
            return;
        if (device.getDeviceType() != DeviceType.PCPANEL_RGB)
            value = Util.map(value, 0, 255, 0, 100);
        Window.devices.get(serialNum).setKnobRotation(knob, value);
        KnobSetting settings = Save.getDeviceSave(serialNum).getKnobSettings()[knob];
        if (settings != null) {
            if (settings.isLogarithmic())
                value = log(value);
            value = Util.map(value, 0, 100, settings.getMinTrim(), settings.getMaxTrim());
        }
        doDialAction(serialNum, knob, value);
    }

    public static void onButtonPress(String serialNum, int knob, boolean pressed) throws IOException {
        Window.devices.get(serialNum).setButtonPressed(knob, pressed);
        if (pressed)
            doClickAction(serialNum, knob);
    }

    private static void doDialAction(String serialNum, int knob, int v) {
        String[] data = Save.getDeviceSave(serialNum).dialData[knob];
        if (data == null || data[0] == null)
            return;
        if (data[0].equals("app_volume")) {
            List<String> cmds = new ArrayList<>(2);
            for (int i = 1; i <= 2; i++) {
                if (data[i] != null && !data[i].equals("")) {
                    String cmd, device = data[3];
                    if (Util.isNullOrEmpty(device)) {
                        cmd = "sndctrl setappvolume \"" + data[i] + "\" " + v;
                    } else {
                        cmd = "sndctrl setappvolume \"" + data[i] + "\" " + v + " \"" + device + "\"";
                    }
                    cmds.add(cmd);
                }
            }
            CommandHandler.pushVolumeChange(serialNum, knob, cmds);
        } else if (data[0].equals("focus_volume")) {
            String cmd = "sndctrl setappvolume focused " + v;
            CommandHandler.pushVolumeChange(serialNum, knob, cmd);
        } else if (data[0].equals("device_volume")) {
            if (Util.isNullOrEmpty(data[1])) {
                String cmd = "sndctrl setsysvolume " + v;
                CommandHandler.pushVolumeChange(serialNum, knob, cmd);
            } else {
                String cmd = "sndctrl setsysvolume " + v + " \"" + data[1] + "\"";
                CommandHandler.pushVolumeChange(serialNum, knob, cmd);
            }
        } else if (data[0].equals("obs_dial")) {
            if (data[1].equals("mix")) {
                String cmd = "obs setsourcevolume \"" + data[2] + "\" " + v;
                CommandHandler.pushVolumeChange(serialNum, knob, cmd);
            }
        } else if (data[0].equals("voicemeeter_dial")) {
            if (!Voicemeeter.login())
                return;
            if (data[1].equals("basic")) {
                Voicemeeter.controlLevel(ControlType.valueOf(data[2]), Util.toInt(data[3], 1), DialType.valueOf(data[4]), v);
            } else if (data[1].equals("advanced")) {
                DialControlMode dt = DialControlMode.valueOf(data[3]);
                if (dt == null)
                    return;
                Voicemeeter.controlLevel(data[2], dt, v);
            }
        }
    }

    private static void doClickAction(String serialNum, int knob) throws IOException {
        String[] data = Save.getDeviceSave(serialNum).buttonData[knob];
        if (data == null || data[0] == null)
            return;
        if (data[0].equals("keystroke")) {
            if (Util.isNullOrEmpty(data[1]))
                return;
            KeyMacro.executeKeyStroke(data[1]);
        } else if (data[0].equals("shortcut")) {
            File file = new File(data[1]);
            if (file.isFile() && Util.isFileExecutable(file)) {
                rt.exec("cmd.exe /c \"" + file.getName() + "\"", null, file.getParentFile());
            } else {
                rt.exec("cmd.exe /c \"" + data[1] + "\"");
            }
        } else if (data[0].equals("media")) {
            if (data[1].equals("media1")) {
                MediaKeys.songPlayPause();
            } else if (data[1].equals("media2")) {
                MediaKeys.mediaStop();
            } else if (data[1].equals("media3")) {
                MediaKeys.songPrevious();
            } else if (data[1].equals("media4")) {
                MediaKeys.songNext();
            } else if (data[1].equals("media5")) {
                MediaKeys.volumeMute();
            }
        } else if (data[0].equals("end_program")) {
            if (data[1].equals("specific")) {
                rt.exec("cmd.exe /c taskkill /IM " + data[2] + " /F");
            } else if (data[1].equals("focused")) {
                rt.exec("sndctrl killfocusedprocess");
            }
        } else if (data[0].equals("sound_device")) {
            rt.exec("sndctrl setdefaultdevice \"" + data[1] + "\"");
        } else if (data[0].equals("toggle_device")) {
            String[] deviceArray = data[1].split("\\|");
            if (deviceArray.length == 0)
                return;
            int index = 0;
            try {
                index = Integer.parseInt(data[2]);
            } catch (Exception exception) {
            }
            if (index >= deviceArray.length)
                index = 0;
            String device = deviceArray[index];
            rt.exec("sndctrl setdefaultdevice \"" + device + "\"");
            data[2] = String.valueOf(index + 1);
        } else if (data[0].equals("mute_app")) {
            rt.exec("sndctrl muteappvolume \"" + data[1] + "\" " + data[2]);
        } else if (data[0].equals("mute_device")) {
            rt.exec("sndctrl mutedevice \"" + data[1] + "\" " + data[2]);
        } else if (data[0].equals("obs_button")) {
            if (data[1].equals("set_scene")) {
                String cmd = "obs setscene \"" + data[2] + "\"";
                CommandHandler.pushVolumeChange(serialNum, knob, cmd);
            } else if (data[1].equals("mute_source")) {
                String cmd = "obs mutesource \"" + data[2] + "\" " + data[3];
                CommandHandler.pushVolumeChange(serialNum, knob, cmd);
            }
        } else if (data[0].equals("voicemeeter_button")) {
            if (!Voicemeeter.login())
                return;
            if (data[1].equals("basic")) {
                Voicemeeter.controlButton(ControlType.valueOf(data[2]), Util.toInt(data[3], 1), ButtonType.valueOf(data[4]));
            } else if (data[1].equals("advanced")) {
                ButtonControlMode bt = ButtonControlMode.valueOf(data[3]);
                if (bt == null)
                    return;
                Voicemeeter.controlButton(data[2], bt);
            }
        } else if (data[0].equals("profile")) {
            if (data[1] == null)
                return;
            Application.invokeAndWait(() -> Window.devices.get(serialNum).setProfile(data[1]));
        }
    }

    private static int log(int x) {
        double in = x;
        double cons = 21.6679065336D;
        double ans = Math.pow(Math.E, in / cons) - 1.0D;
        return (int) Math.round(ans);
    }
}
