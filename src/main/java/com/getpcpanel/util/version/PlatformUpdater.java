package com.getpcpanel.util.version;

/**
 * One platform's self-update transport, selected at runtime by {@link AutoUpdateService} from the way the
 * app is packaged: the Windows Inno Setup installer, a Linux AppImage (zsync), or a Linux Flatpak
 * (OSTree). At most one is {@link #isSupported()} in a given process; the others report false and are
 * never chosen. Implementations are {@code @ApplicationScoped} beans discovered via
 * {@code Instance<PlatformUpdater>}.
 */
public interface PlatformUpdater {
    /** True when this transport can perform an in-place update for the running install. */
    boolean isSupported();

    /** Update to the newest available release of the running channel, then restart. */
    AutoUpdateService.UpdateTarget updateToLatest() throws Exception;

    /**
     * Debug/testing: re-run the update path against the current version, so the mechanism can be exercised
     * without waiting for a newer release.
     */
    AutoUpdateService.UpdateTarget reinstallCurrent() throws Exception;
}
