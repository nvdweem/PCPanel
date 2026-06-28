package com.getpcpanel.util.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.log4j.Log4j2;

/**
 * Opens external targets (the web UI in the default browser, or a folder in the file manager) when
 * requested — from a tray-menu action or a second application instance being started.
 */
@Log4j2
@ApplicationScoped
public class ShowMainService {
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    public void onShowMain(@Observes ShowMainEvent event) {
        openExternally("http://localhost:" + port + "/", false);
    }

    public void onOpenFolder(@Observes OpenFolderEvent event) {
        try {
            // Create the folder if it does not exist yet (e.g. logs before the first rotation) so the
            // file manager has something to open rather than failing silently.
            Files.createDirectories(Path.of(event.path()));
        } catch (IOException e) {
            log.warn("Unable to create folder {}", event.path(), e);
        }
        openExternally(event.path(), true);
    }

    /**
     * Opens a URL or folder via the platform's native command rather than {@code java.awt.Desktop},
     * which would load the AWT toolkit (absent in the macOS native image and the heavy subsystem we
     * are dropping).
     */
    private void openExternally(String target, boolean isFolder) {
        try {
            String[] command;
            if (SystemUtils.IS_OS_WINDOWS) {
                command = isFolder
                        ? new String[] { "explorer", target }
                        : new String[] { "rundll32", "url.dll,FileProtocolHandler", target };
            } else if (SystemUtils.IS_OS_MAC) {
                command = new String[] { "open", target };
            } else {
                command = new String[] { "xdg-open", target };
            }
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.error("Unable to open {}", target, e);
        }
    }
}
