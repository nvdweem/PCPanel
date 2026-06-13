package com.getpcpanel.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

/**
 * Opens the web UI in the default browser when the main window is requested
 * (tray icon click or a second application instance being started).
 */
@Log4j2
@ApplicationScoped
public class ShowMainService {
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    public void onShowMain(@Observes ShowMainEvent event) {
        var url = "http://localhost:" + port + "/";
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            log.warn("Unable to open browser through Desktop, falling back to platform command", e);
        }
        try {
            var command = SystemUtils.IS_OS_WINDOWS
                    ? new String[] { "rundll32", "url.dll,FileProtocolHandler", url }
                    : new String[] { "xdg-open", url };
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.error("Unable to open browser for {}", url, e);
        }
    }
}
