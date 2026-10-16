package com.getpcpanel.util.tray.mac;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The menu bar icon's menu, in order: the same actions as the Windows and Linux tray menus, less the app window,
 * which macOS has no backend for.
 */
enum MacMenuAction {
    OPEN("Open PCPanel", "", false),
    COPY_LINK("Copy UI link", "", false),
    SETTINGS("Open settings folder", "", false),
    REPORT("Report a problem", "", false),
    QUIT("Quit PCPanel", "q", true);

    private final String title;
    private final String keyEquivalent;
    private final boolean separatorBefore;

    MacMenuAction(String title, String keyEquivalent, boolean separatorBefore) {
        this.title = title;
        this.keyEquivalent = keyEquivalent;
        this.separatorBefore = separatorBefore;
    }

    /** The tag AppKit reports for the action's menu item. */
    int tag() {
        return ordinal() + 1;
    }

    static Optional<MacMenuAction> byTag(int tag) {
        return Arrays.stream(values()).filter(action -> action.tag() == tag).findFirst();
    }

    static List<MacAppKit.MenuEntry> menu() {
        return Arrays.stream(values())
                     .map(action -> new MacAppKit.MenuEntry(action.title, action.tag(), action.keyEquivalent, action.separatorBefore))
                     .toList();
    }
}
