package com.getpcpanel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Resolution rules for {@link PcPanelRoot}, the per-user data-directory resolver. Exercises the pure
 * {@code resolve(home, osName, PCPANEL_ROOT, XDG_CONFIG_HOME)} overload so no process environment is
 * touched. The Linux XDG behaviour is what lets the Flatpak (sandbox {@code $HOME}) and immutable
 * distros persist settings, so it is the part worth pinning down.
 */
@DisplayName("PcPanelRoot data-directory resolution")
class PcPanelRootTest {
    private static final String LINUX = "Linux";
    private static final String WINDOWS = "Windows 11";
    private static final String MAC = "Mac OS X";

    @Nested
    @DisplayName("PCPANEL_ROOT override wins on every OS")
    class Override {
        @Test
        void overrideUsedVerbatim() {
            var root = PcPanelRoot.resolve("/home/user", LINUX, "/custom/data", "/home/user/.config");
            assertEquals(Path.of("/custom/data"), root);
        }

        @Test
        void blankOverrideIgnored() {
            var root = PcPanelRoot.resolve("/home/user", WINDOWS, "  ", null);
            assertEquals(Path.of("/home/user/.pcpanel"), root);
        }
    }

    @Nested
    @DisplayName("Windows and macOS keep ~/.pcpanel")
    class NonLinux {
        @Test
        void windows() {
            assertEquals(Path.of("C:/Users/bob/.pcpanel"),
                    PcPanelRoot.resolve("C:/Users/bob", WINDOWS, null, null));
        }

        @Test
        void mac() {
            assertEquals(Path.of("/Users/bob/.pcpanel"),
                    PcPanelRoot.resolve("/Users/bob", MAC, null, null));
        }
    }

    @Nested
    @DisplayName("Linux follows XDG for fresh installs")
    class LinuxFresh {
        @Test
        @DisplayName("XDG_CONFIG_HOME set → $XDG_CONFIG_HOME/pcpanel")
        void xdgSet(@TempDir Path home) {
            var root = PcPanelRoot.resolve(home.toString(), LINUX, null, "/xdg/conf");
            assertEquals(Path.of("/xdg/conf/pcpanel"), root);
        }

        @Test
        @DisplayName("XDG_CONFIG_HOME unset → ~/.config/pcpanel")
        void xdgUnset(@TempDir Path home) {
            var root = PcPanelRoot.resolve(home.toString(), LINUX, null, null);
            assertEquals(home.resolve(".config").resolve("pcpanel"), root);
        }

        @Test
        @DisplayName("blank XDG_CONFIG_HOME → ~/.config/pcpanel")
        void xdgBlank(@TempDir Path home) {
            var root = PcPanelRoot.resolve(home.toString(), LINUX, null, "");
            assertEquals(home.resolve(".config").resolve("pcpanel"), root);
        }
    }

    @Nested
    @DisplayName("Linux keeps a pre-existing legacy ~/.pcpanel")
    class LinuxLegacy {
        @Test
        @DisplayName("legacy dir present → keep it (ignore XDG)")
        void legacyKept(@TempDir Path home) throws IOException {
            Files.createDirectories(home.resolve(".pcpanel"));
            var root = PcPanelRoot.resolve(home.toString(), LINUX, null, "/xdg/conf");
            assertEquals(home.resolve(".pcpanel"), root);
        }

        @Test
        @DisplayName("legacy is a file, not a dir → fall through to XDG")
        void legacyFileIgnored(@TempDir Path home) throws IOException {
            Files.createFile(home.resolve(".pcpanel"));
            var root = PcPanelRoot.resolve(home.toString(), LINUX, null, null);
            assertEquals(home.resolve(".config").resolve("pcpanel"), root);
        }
    }
}
