package com.getpcpanel.util.app;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.appwindow.AppWindowService;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.auth.SessionTokenService;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Opens the UI when asked — from a tray-menu action or a second application instance being started — in the app
 * window or the default browser, as {@link Save#isAppWindow()} says, and opens a folder in the file manager. A change
 * of that setting switches over at once: the app window closes when it is turned off, and the UI opens the new way.
 */
@Log4j2
@ApplicationScoped
public class ShowMainService {
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject SessionTokenService sessionTokens;
    @Inject ProcessHelper processes;
    @Inject SaveService saveService;
    @Inject AppWindowService appWindow;

    /** The setting as last seen, so a save can tell whether it changed. */
    private volatile @Nullable Boolean appWindowMode;

    void onStart(@Observes StartupEvent event) {
        appWindowMode = saveService.get().isAppWindow();
    }

    public void onShowMain(@Observes ShowMainEvent event) {
        showUi(event.redirect());
    }

    void onSave(@Observes SaveService.SaveEvent event) {
        var now = event.save().isAppWindow();
        var before = appWindowMode;
        appWindowMode = now;
        if (before == null || before == now) {
            return;
        }
        log.info("The UI now opens in {}", now ? "the app window" : "the browser");
        if (!now) {
            appWindow.close();
        }
        showUi(null);
    }

    /** Turns the app window setting over (the tray's toggle); the save switches the UI over. */
    public void toggleAppWindow() {
        var save = saveService.get();
        save.setAppWindow(!save.isAppWindow());
        saveService.save();
    }

    public boolean isAppWindow() {
        return saveService.get().isAppWindow();
    }

    private void showUi(@Nullable String redirect) {
        if (saveService.get().isAppWindow() && AppWindowService.isSupported()) {
            appWindow.show(() -> bootstrapUrl(redirect), redirect != null, () -> openInBrowser(redirect));
        } else {
            openInBrowser(redirect);
        }
    }

    private void openInBrowser(@Nullable String redirect) {
        openExternally(bootstrapUrl(redirect), false);
    }

    /**
     * The UI behind the bootstrap handshake, carrying a single-use nonce: the endpoint swaps it for an HttpOnly
     * session cookie and redirects to the app, so only the browser or window opened here is authenticated to the
     * local API. The nonce is worthless once consumed.
     */
    private String bootstrapUrl(@Nullable String redirect) {
        var nonce = sessionTokens.issueNonce();
        var redirectParam = redirect == null ? "" : "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
        return "http://localhost:" + port + "/api/auth/bootstrap?nonce=" + nonce + redirectParam;
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
            processes.launch(command);
        } catch (IOException e) {
            // Drop the query string: the bootstrap URL carries the single-use session nonce there, and it
            // must never reach the log (which other same-user processes and shared bug reports can read).
            log.error("Unable to open {}", redactQuery(target), e);
        }
    }

    /** The target with any query string removed, so a URL's secrets (e.g. the bootstrap nonce) are not logged. */
    private static String redactQuery(String target) {
        var q = target.indexOf('?');
        return q < 0 ? target : target.substring(0, q) + "?<redacted>";
    }
}
