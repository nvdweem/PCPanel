package com.getpcpanel.platform.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.LinuxProcessHelper.ActiveWindow;

class LinuxProcessHelperTest {

    /** The app id lives under the [Application] section; a top-level name= (in [Instance]) must be ignored. */
    @Test
    void parsesApplicationNameFromFlatpakInfo() {
        var info = List.of(
                "[Instance]",
                "name=org.should.NotPickThis",
                "instance-id=12345",
                "",
                "[Application]",
                "name=org.mozilla.firefox",
                "runtime=runtime/org.freedesktop.Platform/x86_64/24.08");

        assertEquals("org.mozilla.firefox", LinuxProcessHelper.parseFlatpakAppId(info));
    }

    @Test
    void returnsNullWhenNoApplicationSection() {
        assertNull(LinuxProcessHelper.parseFlatpakAppId(List.of("[Instance]", "name=org.example")));
        assertNull(LinuxProcessHelper.parseFlatpakAppId(List.of()));
    }

    /**
     * Proton/Wine games' host process often does not match their PulseAudio stream (wrapper process, separate
     * PID namespace, or comm truncated to 15 chars), so the window class and window name must also be exposed as
     * match identifiers. Steam sets the class to steam_app_<id> but leaves the name as the game title (#96).
     */
    @Test
    void identifiersIncludeWindowClassAndName() {
        var window = new ActiveWindow(4321, null, null, "steam_app_945360", "Among Us");

        assertEquals(Set.of("steam_app_945360", "Among Us"), window.identifiers());
    }

    /** Identifiers are a de-duplicated set of the non-blank names; blanks and nulls are dropped. */
    @Test
    void identifiersDeduplicateAndDropBlanks() {
        var window = new ActiveWindow(1, "firefox", "", "firefox", null);

        assertEquals(Set.of("firefox"), window.identifiers());
        assertEquals("firefox", window.primaryIdentifier(), "process is preferred over the (duplicate) window class");
    }

    /** The window name is the last-resort identifier, used for both matching and display when nothing else is known. */
    @Test
    void windowNameIsTheFallbackIdentifier() {
        var window = new ActiveWindow(1, null, null, null, "Deadlock");

        assertEquals(Set.of("Deadlock"), window.identifiers());
        assertEquals("Deadlock", window.primaryIdentifier());
    }
}
