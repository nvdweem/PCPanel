package com.getpcpanel.platform.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.getpcpanel.util.os.ProcessHelper;

/**
 * Runs {@link OsxProcessHelper} against the real {@code lsappinfo} of the machine: Calculator is brought to the
 * front and must come back as the frontmost app, with the same pid {@code lsappinfo list} reports for it. The raw
 * tool output is printed, so a run on a new macOS version shows which format it answers in.
 *
 * <p>Needs a logged-in desktop session (GitHub's macOS runners have one); enable with
 * {@code -Dpcpanel.mactest=true}. The {@code macOS lsappinfo} workflow runs it on GitHub's macOS images.
 */
@EnabledOnOs(OS.MAC)
@EnabledIfSystemProperty(named = "pcpanel.mactest", matches = "true")
class OsxProcessHelperMacTest {
    private static final String APP = "Calculator";
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final ProcessHelper PROCESSES = new ProcessHelper();
    private final OsxProcessHelper sut = new OsxProcessHelper(PROCESSES);

    @BeforeAll
    static void bringAppToFront() throws Exception {
        print("sw_vers");
        PROCESSES.run(WAIT, "open", "-a", APP);
    }

    @AfterAll
    static void quitApp() throws Exception {
        PROCESSES.run(WAIT, "osascript", "-e", "quit app \"" + APP + "\"");
    }

    @Test
    void findsTheFrontmostApp() throws Exception {
        var app = awaitFrontmost();
        var front = print("lsappinfo", "front").stdout().getFirst().trim();
        print("lsappinfo", "info", "-only", "name,pid,executablepath", front);

        assertNotNull(app, "no frontmost application found");
        assertTrue(app.pid() > 0, "no pid in " + app);
        assertTrue(app.executablePath().endsWith("/" + APP), "unexpected executable in " + app);
        assertEquals(app.pid(), sut.foregroundPid().orElse(-1));

        var listed = sut.listApps().stream().filter(a -> a.executablePath().equals(app.executablePath())).findFirst();
        assertTrue(listed.isPresent(), "lsappinfo list does not contain " + app.executablePath());
        assertEquals(app.pid(), listed.get().pid());
    }

    private OsxProcessHelper.FrontmostApp awaitFrontmost() throws InterruptedException {
        var deadline = System.nanoTime() + WAIT.toNanos();
        OsxProcessHelper.FrontmostApp app;
        do {
            app = sut.getFrontmostApp();
            if (app != null && app.executablePath().endsWith("/" + APP)) {
                return app;
            }
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);
        System.out.println("Frontmost application after " + WAIT.toSeconds() + "s: " + app);
        return app;
    }

    private static ProcessHelper.Result print(String... cmd) throws Exception {
        var result = PROCESSES.run(WAIT, cmd);
        System.out.println("$ " + String.join(" ", cmd));
        result.stdout().forEach(System.out::println);
        result.stderr().forEach(System.err::println);
        return result;
    }
}
