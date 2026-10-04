package com.getpcpanel.util.os;

import org.apache.commons.lang3.StringUtils;

/** Runs commands on the host when PCPanel runs in the Flatpak sandbox, where the desktop's programs live. */
public final class FlatpakHost {
    private FlatpakHost() {
    }

    /** Whether PCPanel runs in the Flatpak sandbox ({@code $FLATPAK_ID} is set). */
    public static boolean inSandbox() {
        return StringUtils.isNotBlank(System.getenv("FLATPAK_ID"));
    }

    /** {@code cmd} as it runs on the host: through {@code flatpak-spawn --host} inside the sandbox, unchanged outside. */
    public static String[] command(String... cmd) {
        return inSandbox() ? wrap(cmd) : cmd;
    }

    static String[] wrap(String... cmd) {
        var full = new String[cmd.length + 2];
        full[0] = "flatpak-spawn";
        full[1] = "--host";
        System.arraycopy(cmd, 0, full, 2, cmd.length);
        return full;
    }
}
