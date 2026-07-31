package com.getpcpanel.util.app;

import javax.annotation.Nullable;

/**
 * Event fired when another instance of the application requests the main window to be shown.
 *
 * <p>{@code redirect} is an optional in-app path to land on (e.g. {@link #REPORT_PATH}) so a tray
 * entry can open the UI directly on the screen it names. Absent means the app's start page.
 */
public record ShowMainEvent(@Nullable String redirect) {
    /**
     * The report dialog, raised from the tray for someone whose problem is that the app misbehaves.
     * The dialog is mounted at the app root rather than on a page of its own, so this is the start
     * page carrying a flag the UI consumes and then strips from the URL.
     */
    public static final String REPORT_PATH = "/?report=1";

    public ShowMainEvent() {
        this(null);
    }
}
