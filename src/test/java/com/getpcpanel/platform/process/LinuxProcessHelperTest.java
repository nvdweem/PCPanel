package com.getpcpanel.platform.process;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.getpcpanel.platform.process.LinuxProcessHelper.ActiveWindow;
import com.getpcpanel.platform.process.LinuxProcessHelper.CommandOutput;

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

    /**
     * Pre-2021 xdotool (e.g. 3.20160805 on Linux Mint/Cinnamon) has no {@code getwindowclassname} and aborts the
     * whole chained call on it, printing nothing - so focus volume silently did nothing (#112). The no-class
     * variant must keep the universally supported pid+name and drop only getwindowclassname.
     */
    @Test
    void windowQueryDropsOnlyClassnameWhenUnsupported() {
        assertArrayEquals(new String[] { "getactivewindow", "getwindowpid", "getwindowclassname", "getwindowname" },
                LinuxProcessHelper.windowQuerySubcommands(true));

        var noClass = LinuxProcessHelper.windowQuerySubcommands(false);
        assertArrayEquals(new String[] { "getactivewindow", "getwindowpid", "getwindowname" }, noClass);
        assertFalse(List.of(noClass).contains("getwindowclassname"), "the unsupported subcommand must be dropped");
    }

    /** The window name is the last-resort identifier, used for both matching and display when nothing else is known. */
    @Test
    void windowNameIsTheFallbackIdentifier() {
        var window = new ActiveWindow(1, null, null, null, "Deadlock");

        assertEquals(Set.of("Deadlock"), window.identifiers());
        assertEquals("Deadlock", window.primaryIdentifier());
    }

    /**
     * A tool that ran and said nothing must not look like a tool that was never tried. #151 was reported from a
     * Flatpak, where kdotool is bundled and xdotool is a host-spawn shim, so both always exist — the exit code
     * and stderr are the only things that distinguish "no window is focused" from "this cannot work here", and
     * they used to be discarded.
     */
    @Test
    void failureDetailKeepsWhatTheToolSaid() {
        var failed = new CommandOutput(1, List.of(), "Failed to connect to KWin: ServiceUnknown");

        assertTrue(failed.failureDetail().contains("ServiceUnknown"), "stderr is the whole diagnosis: " + failed.failureDetail());
        assertTrue(failed.failureDetail().contains("exit 1"));
    }

    /** A clean exit with no output is a real outcome too (nothing focused), and must read differently from an error. */
    @Test
    void failureDetailDistinguishesASilentSuccessFromAnError() {
        var quiet = new CommandOutput(0, List.of(), "");

        assertEquals("no window reported", quiet.failureDetail());
    }

    /**
     * On KDE the supported helper is bundled, so a failure is a fault worth quoting. On X11 anywhere else the
     * helper is xdotool, which we do not ship — "install it" is the answer. On a non-KDE Wayland session
     * neither can work: GNOME's Introspect API is allow-listed to the desktop portals and wlroots exposes no
     * equivalent, so the honest answer is that the desktop cannot do this (#151).
     */
    @Test
    void theReasonNamesTheHelperThatCouldHaveWorked() {
        assertTrue(LinuxProcessHelper.focusUnavailableReason("KDE", null, "kdotool: exit 1 - no KWin", "not tried")
                                     .contains("no KWin"), "on KDE, quote what kdotool said");

        assertTrue(LinuxProcessHelper.focusUnavailableReason("XFCE", ":0", "not installed", "xdotool: exit 1")
                                     .contains("Installing xdotool"), "on X11, xdotool is installable and not bundled");

        var wayland = LinuxProcessHelper.focusUnavailableReason("GNOME", null, "not tried", "not tried");
        assertTrue(wayland.contains("GNOME"), wayland);
        assertTrue(wayland.contains("cannot work here"), "say it is unsupported rather than implying misconfiguration: " + wayland);
    }
}
