package com.getpcpanel.util.tray;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.util.tray.awt.AwtTrayImpl;
import com.getpcpanel.util.tray.wayland.TrayServiceWayland;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Picks the system tray implementation at runtime: the AWT tray works on Windows and X11,
 * Wayland sessions need the StatusNotifierItem D-Bus protocol instead.
 * Instantiating the selected bean triggers its {@code @PostConstruct} initialization.
 */
@Log4j2
@ApplicationScoped
public class TrayInitializer {
    @Inject @AwtTrayImpl Instance<ITrayService> awtTray;
    @Inject Instance<TrayServiceWayland> waylandTray;

    void onStart(@Observes StartupEvent event) {
        if (isWayland() && waylandTray.isResolvable()) {
            log.debug("Initializing Wayland tray");
            waylandTray.get();
        } else if (awtTray.isResolvable()) {
            log.debug("Initializing AWT tray");
            awtTray.get();
        } else {
            log.warn("No tray implementation available");
        }
    }

    private static boolean isWayland() {
        return SystemUtils.IS_OS_LINUX
                && (StringUtils.isNotBlank(System.getenv("WAYLAND_DISPLAY"))
                || StringUtils.equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"), "wayland"));
    }
}
