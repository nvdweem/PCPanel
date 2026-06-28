package com.getpcpanel.util.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Resolves the per-user data directory ({@code pcpanel.root} — profiles, logs, lock file) at
 * runtime.
 *
 * <p>This must always be computed from the live {@code user.home}/environment, never cached in a
 * static field or a build-time constant: in a GraalVM native image a static initializer runs at
 * <em>build</em> time and would bake in the build machine's home/environment (e.g. the CI runner's
 * {@code C:\Users\runneradmin}). {@link com.getpcpanel.Main}, {@link FileChecker} and
 * {@link com.getpcpanel.device.provider.pcpanel.HidDebug} each resolve the root through here so the three agree.
 *
 * <p>On Linux the directory follows the XDG Base Directory spec so config lands in a
 * sandbox-friendly, persisted location. This matters for two shipped targets:
 * <ul>
 *   <li><b>Flatpak</b> — inside the sandbox the real {@code $HOME} is the per-app
 *       {@code ~/.var/app/com.getpcpanel.PCPanel/}, and its {@code config/} subdirectory (i.e.
 *       {@code $XDG_CONFIG_HOME}) is the canonical store that survives across runs and upgrades. The
 *       sandbox needs no host-filesystem grant to write there.</li>
 *   <li><b>Immutable distros</b> (Fedora Silverblue/Kinoite, Bazzite, openSUSE Aeon/MicroOS,
 *       SteamOS, …) — only {@code /usr} is read-only; the XDG dirs under {@code $HOME} stay
 *       writable, so an AppImage or a host install both persist here fine.</li>
 * </ul>
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code $PCPANEL_ROOT} when set (any OS) — explicit escape hatch for unusual setups
 *       (read-only home, custom data location, running two installs side by side).</li>
 *   <li>On non-Linux: {@code ~/.pcpanel} (the historical Windows/macOS location, unchanged).</li>
 *   <li>On Linux, the legacy {@code ~/.pcpanel} when it already exists — keeps existing installs
 *       (including older Flatpak installs whose data sits at {@code ~/.var/app/.../.pcpanel}) in
 *       place rather than silently orphaning their profiles.</li>
 *   <li>On Linux otherwise: {@code $XDG_CONFIG_HOME/pcpanel}, or {@code ~/.config/pcpanel} when
 *       {@code XDG_CONFIG_HOME} is unset.</li>
 * </ol>
 */
public final class PcPanelRoot {
    private PcPanelRoot() {
    }

    @SuppressWarnings("AccessOfSystemProperties")
    public static Path resolve() {
        return resolve(System.getProperty("user.home"), System.getProperty("os.name", ""),
                System.getenv("PCPANEL_ROOT"), System.getenv("XDG_CONFIG_HOME"));
    }

    /**
     * Pure resolution with every environment input injected, so it is unit-testable without mutating
     * process state. See the class javadoc for the resolution order. {@code pcpanelRoot} and
     * {@code xdgConfigHome} are the {@code PCPANEL_ROOT} / {@code XDG_CONFIG_HOME} environment values
     * (nullable). The legacy {@code ~/.pcpanel} check is the only filesystem touch.
     */
    static Path resolve(String userHome, String osName, String pcpanelRoot, String xdgConfigHome) {
        if (pcpanelRoot != null && !pcpanelRoot.isBlank()) {
            return Path.of(pcpanelRoot);
        }

        var home = Path.of(userHome);
        if (!isLinux(osName)) {
            return home.resolve(".pcpanel");
        }

        var legacy = home.resolve(".pcpanel");
        if (Files.isDirectory(legacy)) {
            return legacy;
        }

        var base = (xdgConfigHome != null && !xdgConfigHome.isBlank()) ? Path.of(xdgConfigHome) : home.resolve(".config");
        return base.resolve("pcpanel");
    }

    private static boolean isLinux(String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }
}
