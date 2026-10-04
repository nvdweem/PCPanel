package com.getpcpanel.util.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessHelperTest {
    /** Generous upper bound for one call: the deadline itself plus starting a JVM for the fake process. */
    private static final Duration CALL_BUDGET = Duration.ofSeconds(20);
    private static final Duration DEADLINE = Duration.ofSeconds(10);

    private final ProcessHelper sut = new ProcessHelper();

    private static Set<Long> aliveChildren() {
        return ProcessHandle.current().children().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).collect(Collectors.toSet());
    }

    private static String ownValue(String name) {
        return Objects.requireNonNullElse(System.getenv(name), "<unset>");
    }

    @Test
    void runReturnsTheOutputAndExitCode() {
        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.run(DEADLINE, FakeProcess.command("sinks", "3000")));

        assertTrue(result.succeeded());
        assertEquals(3 * 3000, result.stdout().size(), "output far larger than a pipe buffer must be read completely");
    }

    @Test
    void runKeepsStderrApartFromStdout() {
        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.run(DEADLINE, FakeProcess.command("fail", "3", "no such sink")));

        assertFalse(result.timedOut());
        assertEquals(3, result.exitCode());
        assertFalse(result.succeeded());
        assertEquals(List.of("no such sink"), result.stderr());
        assertEquals(List.of(), result.stdout());
    }

    /** A tool stuck on an unresponsive audio server or D-Bus peer must not hold the caller past the deadline. */
    @Test
    void runKillsAHungProcessAtTheDeadline() {
        var before = aliveChildren();

        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.run(Duration.ofMillis(1500), FakeProcess.command("hang")));

        assertTrue(result.timedOut());
        assertFalse(result.succeeded());
        assertEquals(before, aliveChildren(), "the hung process must be killed, not leaked");
    }

    /**
     * xclip and wl-copy exit at once but leave a clipboard server holding the pipes they inherited, so waiting for
     * end-of-output would wait for the server. The tool's own result must still come back promptly.
     */
    @Test
    void runReturnsWhenALeftoverProcessHoldsTheOutputOpen() throws Exception {
        var started = System.nanoTime();
        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.run(DEADLINE, FakeProcess.command("daemonize")));
        var elapsed = Duration.ofNanos(System.nanoTime() - started);
        try {
            assertTrue(result.succeeded(), result.toString());
            assertTrue(elapsed.compareTo(DEADLINE) < 0, "must not wait for the leftover process: took " + elapsed);
        } finally {
            result.stdout().stream().filter(line -> line.matches("\\d+")).findFirst().map(Long::parseLong).flatMap(ProcessHandle::of).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    @Test
    void runWithoutEnvironmentInheritsOursUnchanged() {
        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.run(DEADLINE, FakeProcess.command("echo-env", "LC_ALL")));

        assertEquals(List.of(ownValue("LC_ALL")), result.stdout());
    }

    @Test
    void parseableOutputFixesTheLocale() {
        var result = assertTimeoutPreemptively(CALL_BUDGET,
                () -> sut.run(DEADLINE, ProcessHelper.PARSEABLE_OUTPUT, FakeProcess.command("echo-env", "LC_ALL")));

        assertEquals(List.of("C"), result.stdout());
    }

    @Test
    void runWithInputFeedsStdin() {
        var input = "héllo".getBytes(StandardCharsets.UTF_8);

        var result = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.runWithInput(DEADLINE, input, FakeProcess.command("cat")));

        assertTrue(result.succeeded());
        assertEquals(List.of("héllo"), result.stdout());
    }

    /** A program started from a button must get the user's own locale, not the one we parse tool output in. */
    @Test
    void launchLeavesTheEnvironmentAlone(@TempDir Path dir) throws Exception {
        var file = dir.resolve("env.txt");
        var before = aliveChildren();

        sut.launch(FakeProcess.command("env-to-file", "LC_ALL", file.toString()));

        try {
            assertEquals(ownValue("LC_ALL"), awaitFile(file));
        } finally {
            awaitExitOfChildrenSince(before);
        }
    }

    @Test
    void launchInRunsInThatDirectory(@TempDir Path dir) throws Exception {
        var before = aliveChildren();

        sut.launch(dir.toFile(), FakeProcess.command("env-to-file", "LC_ALL", "relative.txt"));

        try {
            assertEquals(ownValue("LC_ALL"), awaitFile(dir.resolve("relative.txt")));
        } finally {
            awaitExitOfChildrenSince(before);
        }
    }

    @Test
    void streamDeliversEveryLineAndTheExitCode() {
        var lines = new ArrayList<String>();

        var exitCode = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.stream(lines::add, FakeProcess.command("sinks", "2")));

        assertEquals(0, exitCode);
        assertEquals(6, lines.size());
        assertEquals("Sink #1", lines.get(3));
    }

    @Test
    void streamAppliesTheEnvironment() {
        var lines = new ArrayList<String>();

        var exitCode = assertTimeoutPreemptively(CALL_BUDGET,
                () -> sut.stream(ProcessHelper.PARSEABLE_OUTPUT, lines::add, FakeProcess.command("echo-env", "LC_ALL")));

        assertEquals(0, exitCode);
        assertEquals(List.of("C"), lines);
    }

    /** A stream that dies at once ("Connection failure") should say why in the lines it delivers. */
    @Test
    void streamIncludesStderr() {
        var lines = new ArrayList<String>();

        var exitCode = assertTimeoutPreemptively(CALL_BUDGET, () -> sut.stream(lines::add, FakeProcess.command("fail", "1", "Connection failure")));

        assertEquals(1, exitCode);
        assertEquals(List.of("Connection failure"), lines);
    }

    private static String awaitFile(Path file) throws Exception {
        var deadline = System.nanoTime() + CALL_BUDGET.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(file) && Files.size(file) > 0) {
                return Files.readString(file);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the launched process never wrote " + file);
    }

    /**
     * Waits for every process started since {@code before} (and anything it started) to exit. {@link ProcessHelper#launch}
     * doesn't wait, and on Windows a running process keeps its working directory and open files from being deleted,
     * so the {@code @TempDir} cleanup would otherwise race the launched process's shutdown.
     */
    private static void awaitExitOfChildrenSince(Set<Long> before) throws Exception {
        var launched = ProcessHandle.current().children().filter(child -> !before.contains(child.pid())).toList();
        var processes = new ArrayList<ProcessHandle>();
        for (var child : launched) {
            child.descendants().forEach(processes::add);
            processes.add(child);
        }
        var deadline = System.nanoTime() + CALL_BUDGET.toNanos();
        for (var process : processes) {
            try {
                process.onExit().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                processes.forEach(ProcessHandle::destroyForcibly);
                throw new AssertionError("the launched process " + process.pid() + " did not exit", e);
            }
        }
    }
}
