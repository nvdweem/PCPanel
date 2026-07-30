package com.getpcpanel.util.app;

import javax.annotation.Nullable;

/**
 * Event fired when another instance of the application requests the main window to be shown.
 *
 * <p>{@code redirect} is an optional in-app path to land on (e.g. {@code /settings?tab=report}) so a
 * tray entry can open the UI directly on the screen it names. Absent means the app's start page.
 */
public record ShowMainEvent(@Nullable String redirect) {
    /** The report dialog, raised from the tray for someone whose problem is that the app misbehaves. */
    public static final String REPORT_PATH = "/settings?tab=report";

    public ShowMainEvent() {
        this(null);
    }
}
