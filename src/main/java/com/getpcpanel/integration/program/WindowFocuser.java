package com.getpcpanel.integration.program;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Brings a running app's window to the front. One build-time implementation per platform, reached from the command
 * layer via {@link com.getpcpanel.util.CdiHelper}. Best-effort: a failure is logged and reported as
 * {@link Result#NOT_RUNNING}, so the caller starts the app instead.
 */
public interface WindowFocuser {
    enum Result {
        /** No window of the app was found (or none could be brought forward). */
        NOT_RUNNING,
        /** A window of the app is now in front. */
        FOCUSED,
        /** The app was already in front and has been minimised. */
        MINIMIZED,
    }

    /**
     * @param exe               the app's executable, as a path or a name; compared without path, case or a trailing
     *                          {@code .exe}
     * @param minimizeIfFocused minimise the app instead when one of its windows is already in front
     */
    Result focusOrMinimize(String exe, boolean minimizeIfFocused);

    /** The bare app name of {@code exe}: no directory, no trailing {@code .exe}. */
    static String appStem(String exe) {
        return StringUtils.removeEndIgnoreCase(FilenameUtils.getName(StringUtils.strip(exe)), ".exe");
    }
}
