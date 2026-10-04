package com.getpcpanel.integration.program.apps;

/**
 * An app the desktop lists as installed (a Start menu shortcut, a desktop entry, an app bundle).
 *
 * @param name     what the desktop calls it
 * @param target   what opens it: the shortcut, desktop entry or bundle, so its arguments and working directory apply
 * @param exe      the executable or window class its windows are found by
 * @param iconFile the file its icon is read from: the program a shortcut starts, else {@code target}
 */
public record InstalledApp(String name, String target, String exe, String iconFile) {
    public InstalledApp(String name, String target, String exe) {
        this(name, target, exe, target);
    }
}
