package obs;

import lombok.extern.log4j.Log4j2;
import obsremote.OBSRemoteController;
import save.Save;

@Log4j2
public class OBSListener implements Runnable {
    private static final long SLEEP_DURATION = 1000L;
    private static volatile boolean check;
    private static volatile boolean running = true;

    public static void start() {
        new Thread(new OBSListener(), "OBS Listener Thread").start();
    }

    public static void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            sleep();
            synchronized (OBS.OBSMutex) {
                if (!Save.isObsEnabled()) {
                    if (OBS.controller != null && OBS.controller.isConnected()) {
                        log.info("OBS Disconnected");
                        OBS.controller.disconnect();
                        OBS.controller = null;
                    }
                    continue;
                }
                if (OBS.controller == null) {
                    OBS.controller = new OBSRemoteController(Save.getObsAddress(), Save.getObsPort(), Save.getObsPassword());
                } else if (check) {
                    OBS.controller.disconnect();
                    OBS.controller = new OBSRemoteController(Save.getObsAddress(), Save.getObsPort(), Save.getObsPassword());
                } else if (!OBS.controller.isConnected()) {
                    OBS.controller = new OBSRemoteController(Save.getObsAddress(), Save.getObsPort(), Save.getObsPassword());
                }
                check = false;
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(SLEEP_DURATION);
        } catch (InterruptedException e) {
            log.error("OBS Listener Thread was interrupted", e);
        }
    }

    public static void check() {
        check = true;
    }
}
