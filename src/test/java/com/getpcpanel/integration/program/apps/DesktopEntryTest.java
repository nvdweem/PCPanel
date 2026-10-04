package com.getpcpanel.integration.program.apps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class DesktopEntryTest {
    private static final String PATH = "/usr/share/applications/app.desktop";

    @Test
    void readsNameAndTheExecutable() {
        var app = DesktopEntry.parse(PATH, List.of(
                "[Desktop Entry]",
                "Type=Application",
                "Name=Firefox",
                "Name[de]=Feuerfuchs",
                "Exec=/usr/lib/firefox/firefox %u"));

        assertEquals(new InstalledApp("Firefox", PATH, "firefox"), app);
    }

    @Test
    void prefersTheWindowClass() {
        var app = DesktopEntry.parse(PATH, List.of(
                "[Desktop Entry]",
                "Name=Visual Studio Code",
                "Exec=/usr/share/code/code --unity-launch %F",
                "StartupWMClass=Code"));

        assertEquals("Code", app.exe());
    }

    @Test
    void readsOnlyTheMainGroup() {
        var app = DesktopEntry.parse(PATH, List.of(
                "# comment",
                "[Desktop Entry]",
                "Name=Files",
                "Exec=nautilus --new-window %U",
                "",
                "[Desktop Action new-window]",
                "Name=New Window",
                "Exec=other --new-window",
                "NoDisplay=true"));

        assertEquals(new InstalledApp("Files", PATH, "nautilus"), app);
    }

    @Test
    void skipsHiddenAndNonApplicationEntries() {
        assertNull(DesktopEntry.parse(PATH, List.of("[Desktop Entry]", "Name=A", "Exec=a", "NoDisplay=true")));
        assertNull(DesktopEntry.parse(PATH, List.of("[Desktop Entry]", "Name=A", "Exec=a", "Hidden=true")));
        assertNull(DesktopEntry.parse(PATH, List.of("[Desktop Entry]", "Type=Link", "Name=A", "URL=https://example.com")));
        assertNull(DesktopEntry.parse(PATH, List.of("[Desktop Entry]", "Name=A")));
        assertNull(DesktopEntry.parse(PATH, List.of("[Desktop Entry]", "Exec=a")));
    }

    @Test
    void findsTheProgramBehindEnvAndQuotes() {
        assertEquals("app", DesktopEntry.windowClass("env FOO=1 BAR=2 /opt/app/bin/app --flag"));
        assertEquals("app", DesktopEntry.windowClass("\"/opt/My App/app\" %f"));
    }

    @Test
    void aFlatpakAppIsNamedByItsCommandOrId() {
        assertEquals("spotify", DesktopEntry.windowClass(
                "/usr/bin/flatpak run --branch=stable --arch=x86_64 --command=spotify --file-forwarding com.spotify.Client @@u %U @@"));
        assertEquals("org.gnome.Calculator", DesktopEntry.windowClass("/usr/bin/flatpak run --branch=stable --arch=x86_64 org.gnome.Calculator"));
    }
}
