package com.getpcpanel.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.sound.sampled.AudioSystem;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class SoundAudit {
    private SoundAudit() {
    }

    public static List<String> getSoundDevices() {
        List<String> ar = new ArrayList<>();
        try {
            for (var thisMixerInfo : AudioSystem.getMixerInfo()) {
                if (("Direct Audio Device: DirectSound Playback".equals(thisMixerInfo.getDescription()) || "Direct Audio Device: DirectSound Capture".equals(thisMixerInfo.getDescription()))
                        && !"Primary Sound Driver".equals(thisMixerInfo.getName()) &&
                        !ar.contains(convert(thisMixerInfo.getName())))
                    ar.add(convert(thisMixerInfo.getName()));
            }
        } catch (Exception e) {
            log.error("Unable to get sound devices", e);
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
        log.info("{}", getDevices().size());
        for (var device : getDevices())
            log.info("{} {}", device.getCombinedName(), device.getId());
    }

    private static SoundDevice.SoundDeviceType toEnum(String type) {
        if ("eRender".equals(type))
            return SoundDevice.SoundDeviceType.OUTPUT;
        if ("eCapture".equals(type))
            return SoundDevice.SoundDeviceType.INPUT;
        return null;
    }

    public static List<SoundDevice> getDevices() {
        List<SoundDevice> ret = new ArrayList<>();
        try {
            var program = new File("sndctrl.exe");
            var c = new ProcessBuilder(program.toString(), "listdevices");
            var sndctrlProc = c.start();
            var scan = new Scanner(sndctrlProc.getInputStream());
            String x;
            while (!(x = scan.nextLine()).startsWith("Elapsed Milliseconds : "))
                ret.add(new SoundDevice(x, scan.nextLine(), scan.nextLine(), toEnum(scan.nextLine())));
            scan.close();
            sndctrlProc.destroy();
        } catch (Exception e) {
            log.error("Unable to get devices", e);
        }
        return ret;
    }
}

