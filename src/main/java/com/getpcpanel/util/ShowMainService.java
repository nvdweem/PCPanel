package com.getpcpanel.util;

import java.io.IOException;

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
        // Open the browser via the platform's native command rather than java.awt.Desktop, which would
        // load the AWT toolkit (absent in the macOS native image and the heavy subsystem we are dropping).
        try {
            String[] command;
            if (SystemUtils.IS_OS_WINDOWS) {
                command = new String[] { "rundll32", "url.dll,FileProtocolHandler", url };
            } else if (SystemUtils.IS_OS_MAC) {
                command = new String[] { "open", url };
            } else {
                command = new String[] { "xdg-open", url };
            }
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.error("Unable to open browser for {}", url, e);
        }
    }
}
