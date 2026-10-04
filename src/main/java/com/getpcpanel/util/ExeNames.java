package com.getpcpanel.util;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/** How an app is named by its executable: compared without directory, case or a trailing {@code .exe}. */
public final class ExeNames {
    private ExeNames() {
    }

    /**
     * The bare app name of {@code exe}, a path (either slash) or a name: no directory, no surrounding whitespace, no
     * trailing {@code .exe}; case is kept. Empty for a blank or null name.
     */
    public static String stem(@Nullable String exe) {
        if (StringUtils.isBlank(exe)) {
            return "";
        }
        var base = StringUtils.substringAfterLast("/" + exe.replace('\\', '/'), "/");
        return StringUtils.removeEndIgnoreCase(base.strip(), ".exe");
    }

    /** Whether {@code a} and {@code b} name the same app ({@link #stem}, ignoring case); never for a blank name. */
    public static boolean sameApp(@Nullable String a, @Nullable String b) {
        var stem = stem(a);
        return !stem.isEmpty() && stem.equalsIgnoreCase(stem(b));
    }
}
