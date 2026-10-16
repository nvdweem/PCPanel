package com.getpcpanel.util.tray.mac;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.app.CopyUiLinkEvent;
import com.getpcpanel.util.app.OpenFolderEvent;
import com.getpcpanel.util.app.ShowMainEvent;
import com.getpcpanel.util.concurrent.AppThreads;
import com.getpcpanel.util.io.FileUtil;
import com.getpcpanel.util.tray.ITrayService;

import io.quarkus.runtime.Quarkus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The macOS menu bar icon ({@code NSStatusItem}), with the tray menu's actions.
 *
 * <p>AppKit only runs on the process's first thread, which Quarkus hands to {@code Main.run()} once the app has
 * started. {@link #init()} (from {@code TrayInitializer} at startup) therefore only asks for the icon, and
 * {@link #runOnMainThread()} sets it up and runs AppKit's event loop there until Quarkus is asked to exit; the app
 * then shuts down as it does without the icon.
 *
 * <p>Once the app has an application object, macOS delivers a second launch from Finder, Spotlight or the Dock to it
 * as a <em>reopen</em> rather than starting a process, so the reopen opens the UI just as a second process would
 * through {@code FileChecker}. AppKit fails, if at all, before the app checks in with macOS, and the app then runs
 * as it does without the icon: a second launch starts a process and reopens the UI through {@code FileChecker}.
 * Creating the application object is what checks the app in; from then on the event loop runs whatever else
 * fails, since only it can answer a reopen.
 */
@Log4j2
@ApplicationScoped
@MacBuild
public class MacMenuBar implements ITrayService, MacAppKit.Handler {
    private static final String ICON_RESOURCE = "/assets/menubar-template.png";

    @Inject Event<Object> eventBus;
    @Inject FileUtil fileUtil;

    private final ExecutorService actions = Executors.newSingleThreadExecutor(AppThreads.factory("Menu bar actions", true));
    private volatile boolean wanted;

    @Override
    public void init() {
        wanted = true;
    }

    /**
     * Runs the menu bar icon on the calling thread until Quarkus is asked to exit. Returns {@code false} at once, with
     * nothing changed, when the icon is not wanted or AppKit cannot run here; the caller then waits for exit itself.
     */
    public boolean runOnMainThread() {
        if (!wanted) {
            return false;
        }
        if (!MacAppKit.isMainThread()) {
            log.info("No menu bar icon: AppKit needs the process's first thread (the JVM needs -XstartOnFirstThread)");
            return false;
        }
        try {
            // Started first: only this watcher ends the event loop, so without it the app must not run the loop.
            // Stopping a loop that never started does nothing.
            AppThreads.named("Menu bar exit watcher", true, () -> {
                Quarkus.waitForExit();
                MacAppKit.stop();
            }).start();
        } catch (Throwable e) {
            log.warn("No menu bar icon: unable to watch for exit", e);
            return false;
        }
        try {
            MacAppKit.start(this);
        } catch (Throwable e) {
            // Thrown only before the app checked in with macOS, so a second launch still starts a process.
            log.warn("No menu bar icon: AppKit did not start", e);
            return false;
        }
        try {
            MacAppKit.addStatusItem(icon(), "PCPanel", MacMenuAction.menu());
            log.info("Menu bar icon shown");
        } catch (Throwable e) {
            // The application object is up, so a reopen still reaches reopen() and opens the UI.
            log.error("Unable to show the menu bar icon", e);
        }
        MacAppKit.runEventLoop();
        return true;
    }

    @Override
    public void menuItemClicked(int tag) {
        MacMenuAction.byTag(tag).ifPresent(action -> {
            if (action == MacMenuAction.QUIT) {
                Quarkus.asyncExit(); // does not block, and must not queue behind a slow action
            } else {
                actions.execute(() -> eventFor(action).ifPresent(eventBus::fire));
            }
        });
    }

    @Override
    public void reopen() {
        log.info("Showing the UI because PCPanel was opened again");
        actions.execute(() -> eventBus.fire(new ShowMainEvent()));
    }

    @Override
    @SuppressWarnings("CallToSystemExit")
    public void terminateRequested() {
        log.info("macOS asked PCPanel to quit");
        // AppKit holds the main thread in its own loop until the quit is answered, so Main.run() cannot return to let
        // Quarkus stop. Exit as the Windows tray's Exit does: System.exit runs Quarkus' shutdown hook, which stops the
        // app without the main thread, and the process ending answers macOS.
        AppThreads.named("Quit for macOS", false, () -> System.exit(0)).start();
    }

    /** The event a menu action fires; empty for {@link MacMenuAction#QUIT}, which exits the app instead. */
    Optional<Object> eventFor(MacMenuAction action) {
        return switch (action) {
            case OPEN -> Optional.of(new ShowMainEvent());
            case COPY_LINK -> Optional.of(new CopyUiLinkEvent());
            case SETTINGS -> Optional.of(new OpenFolderEvent(fileUtil.getRoot().toString()));
            case REPORT -> Optional.of(new ShowMainEvent(ShowMainEvent.REPORT_PATH));
            case QUIT -> Optional.empty();
        };
    }

    private static byte[] icon() throws IOException {
        try (var in = MacMenuBar.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing " + ICON_RESOURCE);
            }
            return in.readAllBytes();
        }
    }
}
