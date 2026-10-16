package com.getpcpanel.util.tray.mac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class MacMenuActionTest {
    @Test
    void everyTagFindsItsAction() {
        for (var action : MacMenuAction.values()) {
            assertEquals(Optional.of(action), MacMenuAction.byTag(action.tag()));
        }
        assertEquals(MacMenuAction.values().length, Arrays.stream(MacMenuAction.values()).mapToInt(MacMenuAction::tag).distinct().count());
    }

    @Test
    void unknownTagsFindNothing() {
        // 0 is the tag of an item that never had one set.
        assertTrue(MacMenuAction.byTag(0).isEmpty());
        assertTrue(MacMenuAction.byTag(MacMenuAction.values().length + 1).isEmpty());
    }

    @Test
    void menuOpensWithOpenAndEndsWithQuitBehindASeparator() {
        var menu = MacMenuAction.menu();

        assertEquals(MacMenuAction.values().length, menu.size());
        assertEquals("Open PCPanel", menu.getFirst().title());
        assertFalse(menu.getFirst().separatorBefore());
        var quit = menu.getLast();
        assertEquals("Quit PCPanel", quit.title());
        assertEquals("q", quit.keyEquivalent());
        assertTrue(quit.separatorBefore());
        assertEquals(MacMenuAction.QUIT.tag(), quit.tag());
    }
}
