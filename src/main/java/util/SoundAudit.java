package util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer.Info;

import util.SoundDevice.SoundDeviceType;

public class SoundAudit {
    public static List<String> getSoundDevices() {
        List<String> ar = new ArrayList<>();
        try {
            byte b;
            int i;
            Info[] arrayOfInfo;
            for (i = (arrayOfInfo = AudioSystem.getMixerInfo()).length, b = 0; b < i; ) {
                Info thisMixerInfo = arrayOfInfo[b];
                if ((thisMixerInfo.getDescription().equals("Direct Audio Device: DirectSound Playback") || thisMixerInfo.getDescription().equals("Direct Audio Device: DirectSound Capture"))
                        && !thisMixerInfo.getName().equals("Primary Sound Driver") &&
                        !ar.contains(convert(thisMixerInfo.getName())))
                    ar.add(convert(thisMixerInfo.getName()));
                b++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ar;
    }

    private static String convert(String x) {
        if (x.contains("("))
            x = x.substring(0, x.indexOf("("));
        if (x.charAt(x.length() - 1) == ' ')
            x = x.substring(0, x.length() - 1);
        return x;
    }

    public static void main(String[] args) throws Exception {
        System.err.println(getDevices().size());
        for (SoundDevice device : getDevices())
            System.err.println(device.getCombinedName() + " " + device.getId());
    }

    private static SoundDeviceType toEnum(String type) {
        if (type.equals("eRender"))
            return SoundDeviceType.OUTPUT;
        if (type.equals("eCapture"))
            return SoundDeviceType.INPUT;
        return null;
    }

    public static List<SoundDevice> getDevices() {
        List<SoundDevice> ret = new ArrayList<>();
        try {
            File program = new File("sndctrl.exe");
            ProcessBuilder c = new ProcessBuilder(program.toString(), "listdevices");
            Process sndctrlProc = c.start();
            Scanner scan = new Scanner(sndctrlProc.getInputStream());
            String x;
            while (!(x = scan.nextLine()).startsWith("Elapsed Milliseconds : "))
                ret.add(new SoundDevice(x, scan.nextLine(), scan.nextLine(), toEnum(scan.nextLine())));
            scan.close();
            sndctrlProc.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }
}

