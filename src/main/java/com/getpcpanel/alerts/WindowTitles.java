package com.getpcpanel.alerts;

import java.util.List;
import java.util.Map;

/**
 * The titles of the windows on screen. One implementation per platform. A failure to read them is thrown, for
 * {@link AlertService} to report.
 */
public interface WindowTitles {
    /** Visible top-level window titles per exe name (lower-case, no .exe). */
    Map<String, List<String>> titles();

    /** Titles are not needed for now: lets go of what reading them holds open. The next {@link #titles()} starts over. */
    default void release() {
    }
}
