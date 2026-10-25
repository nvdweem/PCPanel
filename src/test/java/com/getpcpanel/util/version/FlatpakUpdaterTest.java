package com.getpcpanel.util.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.getpcpanel.util.os.ProcessHelper;
import com.getpcpanel.util.version.FlatpakUpdater.Install;
import com.getpcpanel.util.version.FlatpakUpdater.Scope;

class FlatpakUpdaterTest {
    private static final String APP = "com.getpcpanel.PCPanel";

    @Test
    void userInstallFromItsFlatpakInfo() {
        var install = Install.parse(List.of(
                "[Application]",
                "name=com.getpcpanel.PCPanel",
                "",
                "[Instance]",
                "instance-id=3318442330",
                "app-path=/home/niels/.local/share/flatpak/app/com.getpcpanel.PCPanel/x86_64/test/2e4d6bf69a49/files",
                "app-commit=2e4d6bf69a49806c2770bc8dfa5fa16885349ccdd65bfa1b842d49667b67070e",
                "branch=test",
                "",
                "[Environment]",
                "branch=not-this-one"));

        assertEquals("2e4d6bf69a49806c2770bc8dfa5fa16885349ccdd65bfa1b842d49667b67070e", install.commit());
        assertEquals(Scope.USER, install.scope());
        assertEquals(APP + "//test", install.ref(APP));
        assertEquals(List.of("--user"), install.scopeArgs());
        assertEquals(List.of("--branch=test"), install.branchArgs());
    }

    @Test
    void systemInstall() {
        var install = Install.parse(List.of(
                "[Instance]",
                "app-path=/var/lib/flatpak/app/com.getpcpanel.PCPanel/x86_64/snapshot/7d5bf6e5a34b/files",
                "app-commit=7d5bf6e5a34b",
                "branch=snapshot"));

        assertEquals(Scope.SYSTEM, install.scope());
        assertEquals(List.of("--system"), install.scopeArgs());
        assertEquals(APP + "//snapshot", install.ref(APP));
    }

    /** A missing or unusual file leaves the update as a plain `flatpak update <app id>` that never counts as unchanged. */
    @Test
    void unknownInstallFallsBackToTheAppId() {
        var install = Install.parse(List.of("[Instance]", "app-path=/opt/custom-installation/app/x/files"));

        assertNull(install.commit());
        assertNull(install.scope());
        assertEquals(APP, install.ref(APP));
        assertEquals(List.of(), install.scopeArgs());
        assertEquals(List.of(), install.branchArgs());
    }

    /** The real output of a system-wide update from the unsigned PCPanel repository without root. */
    @Test
    void errorLinePrefersTheOperationsError() {
        var result = new ProcessHelper.Result(false, 1,
                List.of("Looking for updates…", "", "        ID                     Branch   Op Remote         Download",
                        " 1. [✗] com.getpcpanel.PCPanel snapshot u  pcpanel-origin < 38,9 MB", "",
                        "Error: Can't pull from untrusted non-gpg verified remote", "Updates complete."),
                List.of("error: There were one or more errors"));

        assertEquals("Error: Can't pull from untrusted non-gpg verified remote", FlatpakUpdater.errorLine(result));
    }

    @Test
    void errorLineFallsBackToTheLastLine() {
        var result = new ProcessHelper.Result(false, 1, List.of("Looking for updates…"), List.of("error: Nothing matches com.getpcpanel.PCPanel", ""));

        assertEquals("error: Nothing matches com.getpcpanel.PCPanel", FlatpakUpdater.errorLine(result));
        assertNull(FlatpakUpdater.errorLine(new ProcessHelper.Result(false, 1, List.of(), List.of(" "))));
    }
}
