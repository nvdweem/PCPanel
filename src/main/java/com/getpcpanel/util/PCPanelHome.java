package com.getpcpanel.util;

import java.io.File;

/**
 * Resolves the directory where PCPanel stores its data ({@code profiles.json}, lock files, logs, ...).
 * <p>
 * This MUST be resolved at run time. In a GraalVM native image the {@code ${user.home}} expression in
 * {@code application.properties} and any {@code System.getProperty("user.home")} captured during build-time
 * class initialization are frozen to the <em>build machine's</em> home (e.g. {@code /home/runner} on CI).
 * On the user's machine that path does not exist / is not writable, so saves silently fail and every restart
 * comes up vanilla. Resolving here, lazily, from the live process environment avoids that. It also makes the
 * path correct inside Flatpak/AppImage sandboxes, which expose their own writable {@code $HOME}.
 *
 * @see Main#main(String...) which seeds {@code pcpanel.root} from this in a native image
 */
public final class PCPanelHome {
    /** Optional explicit override of the full data directory (absolute path), handy for immutable distros. */
    private static final String OVERRIDE_ENV = "PCPANEL_HOME";
    private static final String DIR_NAME = ".pcpanel";

    private PCPanelHome() {
    }

    /** The PCPanel data directory, resolved from the live environment at the moment of the call. */
    @SuppressWarnings("AccessOfSystemProperties")
    public static File resolve() {
        var override = System.getenv(OVERRIDE_ENV);
        if (override != null && !override.isBlank()) {
            return new File(override);
        }
        // Prefer $HOME (always read live) over the user.home system property, which a native image may have
        // frozen at build time.
        var home = System.getenv("HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home");
        }
        return new File(home, DIR_NAME);
    }
}
