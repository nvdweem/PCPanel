package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import obs.OBS;

public class CommandHandler {
    private static final Map<String, List<String>> map = new ConcurrentHashMap<>();

    private static final Object waiter = new Object();

    private static final Runtime rt = Runtime.getRuntime();

    private static Scanner scan;

    private static OutputStream out;

    private static Process sndctrlProc;

    static {
        ProcessBuilder c = new ProcessBuilder("sndctrl");
        try {
            sndctrlProc = c.start();
            out = sndctrlProc.getOutputStream();
            InputStream in = sndctrlProc.getInputStream();
            scan = new Scanner(in);
        } catch (IOException e1) {
            System.err.println("UNABLE TO START SNDCTRL");
            e1.printStackTrace();
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
        for (String cmd : cmds) {
            if (cmd.startsWith("sndctrl ") && sndctrlProc != null) {
                String input = cmd.substring(8);
                try {
                    out.write((input + "\n").getBytes());
                    out.flush();
                    scan.nextLine();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                continue;
            }
            if (cmd.startsWith("obs ")) {
                if (!OBS.isConnected())
                    continue;
                String[] args = cmd.split("\"?( |$)(?=(([^\"]*\"){2})*[^\"]*$)\"?");
                if (args[1].equals("setsourcevolume")) {
                    OBS.setSourceVolume(args[2], Util.toInt(args[3], 0));
                    continue;
                }
                if (args[1].equals("setscene")) {
                    OBS.setCurrentScene(args[2]);
                    continue;
                }
                if (args[1].equals("mutesource")) {
                    if (args[3].equals("toggle")) {
                        OBS.toggleSourceMute(args[2]);
                        continue;
                    }
                    if (args[3].equals("mute")) {
                        OBS.setSourceMute(args[2], true);
                        continue;
                    }
                    if (args[3].equals("unmute"))
                        OBS.setSourceMute(args[2], false);
                }
                continue;
            }
            try {
                Process p = rt.exec(cmd);
                p.waitFor(1L, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class HandlerThread extends Thread {
        @Override
        public void run() {
            boolean foundAnyOnPrevSweep = false;
            while (true) {
                if (!foundAnyOnPrevSweep)
                    waitFunc();
                foundAnyOnPrevSweep = false;
                for (String key : CommandHandler.map.keySet()) {
                    List<String> cmds = CommandHandler.map.get(key);
                    if (cmds == null)
                        continue;
                    CommandHandler.map.remove(key);
                    CommandHandler.dispatchSndCtrl(cmds);
                    foundAnyOnPrevSweep = true;
                }
            }
        }

        private void waitFunc() {
            try {
                synchronized (CommandHandler.waiter) {
                    CommandHandler.waiter.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

