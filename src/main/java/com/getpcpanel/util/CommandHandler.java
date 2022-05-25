package com.getpcpanel.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.getpcpanel.obs.OBS;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CommandHandler {
    private static final Map<String, List<String>> map = new ConcurrentHashMap<>();
    private static final Object waiter = new Object();
    private static final Runtime rt = Runtime.getRuntime();
    private static Scanner scan;
    private static OutputStream out;
    private static Process sndctrlProc;

    static {
        var c = new ProcessBuilder("sndctrl");
        try {
            sndctrlProc = c.start();
            out = sndctrlProc.getOutputStream();
            var in = sndctrlProc.getInputStream();
            scan = new Scanner(in);
        } catch (IOException e1) {
            log.error("UNABLE TO START SNDCTRL", e1);
        }
        new Thread(new HandlerThread(), "Command Handler Thread").start();
    }

    public static void pushVolumeChange(String serialNum, int knob, String... cmds) {
        pushVolumeChange(serialNum, knob, Arrays.asList(cmds));
    }

    public static void pushVolumeChange(String serialNum, int knob, List<String> cmds) {
        map.put(String.valueOf(serialNum) + knob, cmds);
        synchronized (waiter) {
            waiter.notify();
        }
    }

    private static void dispatchSndCtrl(List<String> cmds) {
        for (var cmd : cmds) {
            if (cmd.startsWith("sndctrl ") && sndctrlProc != null) {
                var input = cmd.substring(8);
                try {
                    out.write((input + "\n").getBytes());
                    out.flush();
                    scan.nextLine();
                } catch (Exception e) {
                    log.error("Unable to write in dispatchSndCtrl", e);
                }
                continue;
            }
            if (cmd.startsWith("obs ")) {
                if (!OBS.isConnected())
                    continue;
                var args = cmd.split("\"?( |$)(?=(([^\"]*\"){2})*[^\"]*$)\"?");
                if ("setsourcevolume".equals(args[1])) {
                    OBS.setSourceVolume(args[2], Util.toInt(args[3], 0));
                    continue;
                }
                if ("setscene".equals(args[1])) {
                    OBS.setCurrentScene(args[2]);
                    continue;
                }
                if ("mutesource".equals(args[1])) {
                    if ("toggle".equals(args[3])) {
                        OBS.toggleSourceMute(args[2]);
                        continue;
                    }
                    if ("mute".equals(args[3])) {
                        OBS.setSourceMute(args[2], true);
                        continue;
                    }
                    if ("unmute".equals(args[3]))
                        OBS.setSourceMute(args[2], false);
                }
                continue;
            }
            try {
                var p = rt.exec(cmd);
                p.waitFor(1L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Unable to wait for '{}' to load", cmd, e);
            }
        }
    }

    public static class HandlerThread extends Thread {
        @Override
        public void run() {
            var foundAnyOnPrevSweep = false;
            while (true) {
                if (!foundAnyOnPrevSweep)
                    waitFunc();
                foundAnyOnPrevSweep = false;
                for (var entry : map.entrySet()) {
                    var cmds = entry.getValue();
                    if (cmds == null)
                        continue;
                    map.remove(entry.getKey());
                    dispatchSndCtrl(cmds);
                    foundAnyOnPrevSweep = true;
                }
            }
        }

        private void waitFunc() {
            try {
                synchronized (waiter) {
                    waiter.wait();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
            }
        }
    }
}

