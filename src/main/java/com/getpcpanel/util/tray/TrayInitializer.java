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
 * Picks the system tray implementation at runtime: Linux (both X11 and Wayland) uses the AWT-free
 * StatusNotifierItem D-Bus tray; Windows uses the AWT {@link com.getpcpanel.util.tray.awt.TrayServiceAwt}.
 * macOS has no tray yet (it would need a native NSStatusItem implementation).
 * The chosen bean's {@link ITrayService#init()} is invoked explicitly - the tray beans are lazy
 * {@code @ApplicationScoped} client proxies, so merely calling {@code Instance.get()} does not
 * create the instance (and would never run a {@code @PostConstruct}); a real method call does.
 */
@Log4j2
@ApplicationScoped
public class TrayInitializer {
    @Inject @AwtTrayImpl Instance<ITrayService> awtTray;
    @Inject Instance<TrayServiceWayland> sniTray;

    void onStart(@Observes StartupEvent event) {
        if (isTrayDisabled()) {
            log.info("Tray disabled (disable.tray / PCPANEL_DISABLE_TRAY); running without a system tray icon");
            return;
        }
        if (SystemUtils.IS_OS_LINUX && sniTray.isResolvable()) {
            // StatusNotifierItem over D-Bus is the modern, AWT-free tray protocol; it works on both
            // Wayland and X11 desktops that run an SNI host (KDE, GNOME+AppIndicator, etc.).
            log.debug("Initializing StatusNotifierItem (D-Bus) tray");
            sniTray.get().init();
        } else if (SystemUtils.IS_OS_WINDOWS && awtTray.isResolvable()) {
            log.debug("Initializing AWT tray");
            awtTray.get().init();
        } else {
            log.warn("No tray implementation available");
        }
    }

    /**
     * The tray can be turned off via the documented {@code -Ddisable.tray} JVM system property
     * (presence is enough, e.g. {@code -Ddisable.tray}), or via the {@code PCPANEL_DISABLE_TRAY}
     * environment variable. The env var is a robust alternative for launchers where extra args are
     * not forwarded to the JVM as {@code -D} properties (see #90).
     */
    private static boolean isTrayDisabled() {
        if (System.getProperty("disable.tray") != null) {
            return true;
        }
        var env = System.getenv("PCPANEL_DISABLE_TRAY");
        return StringUtils.isNotBlank(env) && !StringUtils.equalsAnyIgnoreCase(env, "0", "false", "no");
    }
}
