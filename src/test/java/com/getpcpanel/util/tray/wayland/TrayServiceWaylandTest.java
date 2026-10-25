package com.getpcpanel.util.tray.wayland;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrayServiceWaylandTest {
    /** The Background portal rejects a status message longer than 96 characters, and it must be a single line. */
    @Test
    void duplicateEntryMessageFitsThePortal() {
        var message = TrayServiceWayland.DUPLICATE_ENTRY_MESSAGE;
        assertTrue(message.codePointCount(0, message.length()) <= 96, () -> message.length() + " characters: " + message);
        assertFalse(message.contains("\n") || message.contains("\r"));
    }
}
