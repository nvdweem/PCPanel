package com.getpcpanel.integration.volume.overlay;

import com.sun.jna.Platform;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

/**
 * Development/diagnostic aid: when started with {@code -Dpcpanel.overlay.demo=true}, repeatedly
 * shows the volume overlay on startup, sweeping the value up and down. This exercises the overlay
 * window (the path that crashed the native Windows build) without needing a physical fader.
 *
 * <p>Inert unless the system property is set, so it is safe to leave in the build. Remove once the
 * native overlay is confirmed stable.
 */
@Log4j2
@ApplicationScoped
class OverlayDemoTrigger {
    void onStart(@Observes StartupEvent event) {
        if (!Boolean.getBoolean("pcpanel.overlay.demo")) {
            return;
        }
        log.info("Overlay demo enabled – sweeping the overlay value on startup");
        var thread = new Thread(this::sweep, "PCPanel Overlay Demo");
        thread.setDaemon(true);
        thread.start();
    }

    private void sweep() {
        OverlayWindow overlay = Platform.isWindows() ? new Win32VolumeOverlay() : new NoOpOverlayWindow();
        var screen = overlay.getScreenSize();
        overlay.setLocation((screen.width() - overlay.getWidth()) / 2, 120);
        try {
            for (var cycle = 0; cycle < 4; cycle++) {
                for (var v = 0; v <= 100; v += 5) {
                    overlay.show(new OverlayContent(v / 100f, null, "Demo App", null));
                    Thread.sleep(80);
                }
                for (var v = 100; v >= 0; v -= 5) {
                    overlay.show(new OverlayContent(v / 100f, null, "Demo App", null));
                    Thread.sleep(80);
                }
            }
            log.info("Overlay demo sweep finished without crashing");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
