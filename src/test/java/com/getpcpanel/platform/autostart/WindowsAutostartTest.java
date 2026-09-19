package com.getpcpanel.platform.autostart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.getpcpanel.rest.model.dto.AutostartStateDto;

class WindowsAutostartTest {
    private static final String EXE = "C:\\Users\\Jane Doe\\AppData\\Local\\Programs\\PCPanel\\PCPanel.exe";

    /** Records every command and answers each by its executable: exit code, or 1 when unlisted. */
    private static class FakeRunner implements WindowsAutostart.CommandRunner {
        final List<List<String>> commands = new ArrayList<>();
        /** Whether the Run value "exists" as far as {@code reg query} is concerned; flips on add/delete. */
        boolean runValue;
        boolean task;

        FakeRunner(boolean runValue, boolean task) {
            this.runValue = runValue;
            this.task = task;
        }

        @Override
        public int run(List<String> command) {
            commands.add(command);
            var exe = command.getFirst();
            return switch (exe) {
                case "reg.exe" -> switch (command.get(1)) {
                    case "query" -> runValue ? 0 : 1;
                    case "delete" -> { runValue = false; yield 0; }
                    default -> 2;
                };
                case "schtasks.exe" -> task ? 0 : 1;
                case "powershell.exe" -> { runValue = true; yield 0; }
                default -> 2;
            };
        }

        List<String> only(String exe) {
            var hits = commands.stream().filter(c -> c.getFirst().equals(exe)).toList();
            assertEquals(1, hits.size(), "expected exactly one " + exe + " call, got " + commands);
            return hits.getFirst();
        }

        boolean ran(String exe) {
            return commands.stream().anyMatch(c -> c.getFirst().equals(exe));
        }
    }

    private static WindowsAutostart supported(FakeRunner runner) {
        return new WindowsAutostart(runner, () -> Optional.of(EXE), true);
    }

    @Test
    void unsupportedPlatformReportsNothingAndRefusesChanges() {
        var runner = new FakeRunner(true, true);
        var autostart = new WindowsAutostart(runner, () -> Optional.of(EXE), false);
        assertEquals(new AutostartStateDto(false, false, false), autostart.state());
        assertThrows(WindowsAutostart.AutostartException.class, () -> autostart.set(true));
        assertTrue(runner.commands.isEmpty(), "must not touch the machine when unsupported");
    }

    @Test
    void stateComesFromTheRunValueAndTheScheduledTask() {
        assertEquals(new AutostartStateDto(true, false, false), supported(new FakeRunner(false, false)).state());
        assertEquals(new AutostartStateDto(true, true, false), supported(new FakeRunner(true, false)).state());
        assertEquals(new AutostartStateDto(true, true, true), supported(new FakeRunner(false, true)).state());
    }

    @Test
    void stateQueriesUseTheInstallersNames() {
        var runner = new FakeRunner(false, false);
        supported(runner).state();
        assertEquals(List.of("reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "PCPanel"), runner.only("reg.exe"));
        assertEquals(List.of("schtasks.exe", "/Query", "/TN", "PCPanel"), runner.only("schtasks.exe"));
    }

    @Test
    void enablingWritesTheInstallersRunValueThroughPowerShell() {
        var runner = new FakeRunner(false, false);
        supported(runner).set(true);

        var ps = runner.only("powershell.exe");
        assertEquals(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand"), ps.subList(0, 6));
        var script = new String(Base64.getDecoder().decode(ps.get(6)), StandardCharsets.UTF_16LE);
        assertEquals("Set-ItemProperty -LiteralPath 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name 'PCPanel' -Type String"
                + " -Value '\"" + EXE + "\" quiet'", script);
        assertTrue(supported(runner).state().enabled());
    }

    @Test
    void singleQuotesInTheExePathAreEscapedForPowerShell() {
        var runner = new FakeRunner(false, false);
        new WindowsAutostart(runner, () -> Optional.of("C:\\Users\\O'Brien\\PCPanel.exe"), true).set(true);
        var script = new String(Base64.getDecoder().decode(runner.only("powershell.exe").get(6)), StandardCharsets.UTF_16LE);
        assertTrue(script.endsWith("-Value '\"C:\\Users\\O''Brien\\PCPanel.exe\" quiet'"), script);
    }

    @Test
    void disablingDeletesTheRunValue() {
        var runner = new FakeRunner(true, false);
        supported(runner).set(false);
        var deletes = runner.commands.stream().filter(c -> c.get(1).equals("delete")).toList();
        assertEquals(List.of(List.of("reg.exe", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "PCPanel", "/f")), deletes);
        assertFalse(supported(runner).state().enabled());
    }

    @Test
    void disablingWhenAlreadyOffDoesNotDelete() {
        var runner = new FakeRunner(false, false);
        supported(runner).set(false);
        assertFalse(runner.commands.stream().anyMatch(c -> c.get(1).equals("delete")), "nothing to delete: " + runner.commands);
    }

    @Test
    void theElevatedTaskBlocksBothDirections() {
        var runner = new FakeRunner(false, true);
        var autostart = supported(runner);
        assertThrows(WindowsAutostart.AutostartException.class, () -> autostart.set(true));
        assertThrows(WindowsAutostart.AutostartException.class, () -> autostart.set(false));
        assertFalse(runner.ran("powershell.exe"));
        assertFalse(runner.commands.stream().anyMatch(c -> c.get(1).equals("delete")));
    }

    @Test
    void aWriteThatDoesNotStickIsReported() {
        var runner = new FakeRunner(false, false) {
            @Override
            public int run(List<String> command) {
                var code = super.run(command);
                if (command.getFirst().equals("powershell.exe")) {
                    runValue = false; // the value never appeared
                }
                return code;
            }
        };
        assertThrows(WindowsAutostart.AutostartException.class, () -> supported(runner).set(true));
    }

    @Test
    void anUnknownExePathIsReported() {
        var runner = new FakeRunner(false, false);
        var autostart = new WindowsAutostart(runner, Optional::empty, true);
        assertThrows(WindowsAutostart.AutostartException.class, () -> autostart.set(true));
        assertFalse(runner.ran("powershell.exe"));
    }
}
