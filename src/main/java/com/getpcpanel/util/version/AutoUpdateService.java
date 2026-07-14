package com.getpcpanel.util.version;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Self-update façade. Everything that drives updates (the REST {@code SystemResource}, the startup
 * {@link VersionChecker}, and {@code PlatformResource} for the UI capability flag) injects this bean. It
 * picks the one {@link PlatformUpdater} transport that matches how the app is packaged and delegates to
 * it:
 * <ul>
 *   <li>{@link WindowsInstallerUpdater} — Windows Inno Setup installer, downloaded and run silently.</li>
 *   <li>{@link AppImageUpdater} — Linux AppImage, updated in place via zsync.</li>
 *   <li>{@link FlatpakUpdater} — Linux Flatpak, updated from its OSTree remote.</li>
 * </ul>
 * When no transport supports the running install — a Linux {@code .deb}, a dev/JVM run, or macOS —
 * {@link #isSupported()} is false and the UI keeps linking to the release download page instead of
 * offering a one-click "Update &amp; restart".
 */
@Log4j2
@ApplicationScoped
public class AutoUpdateService {
    @Inject Instance<PlatformUpdater> updaters;

    /** The single transport that supports the running install, or null when none does. */
    private PlatformUpdater active() {
        return updaters.stream().filter(PlatformUpdater::isSupported).findFirst().orElse(null);
    }

    /** True when an in-place update can run for this install (Windows installer, AppImage, or Flatpak). */
    public boolean isSupported() {
        return active() != null;
    }

    /** Update to the newest available release, then restart. */
    public UpdateTarget updateToLatest() throws Exception {
        return require().updateToLatest();
    }

    /** Debug/testing: re-run the update path against the currently running version, to exercise it. */
    public UpdateTarget reinstallCurrent() throws Exception {
        return require().reinstallCurrent();
    }

    private PlatformUpdater require() {
        var active = active();
        if (active == null) {
            throw new UpdateException("Automatic updates are not available for this installation.");
        }
        return active;
    }

    /** The release we are about to install: its display version and (Windows only) the installer URL. */
    @io.quarkus.runtime.annotations.RegisterForReflection
    public record UpdateTarget(String version, String installerUrl) {}

    /** A user-facing update failure; its message is surfaced to the UI. */
    public static class UpdateException extends RuntimeException {
        public UpdateException(String message) {
            super(message);
        }
    }
}
