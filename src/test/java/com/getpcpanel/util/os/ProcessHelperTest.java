package com.getpcpanel.util.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ProcessHelperTest {
    /** Generous upper bound for one call: the deadline itself plus starting a JVM for the fake process. */
    private static final Duration CALL_BUDGET = Duration.ofSeconds(20);

    private static Process start(String... args) throws Exception {
        return new ProcessHelper().builder(FakeProcess.command(args)).start();
    }

    static Set<Long> aliveChildren() {
        return ProcessHandle.current().children().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).collect(Collectors.toSet());
    }

    @Test
    void returnsTheOutputOfAProcessThatFinishes() {
        var lines = assertTimeoutPreemptively(CALL_BUDGET, () -> ProcessHelper.readWithDeadline(start("sinks", "3000"), 10_000));

        assertTrue(lines.isPresent());
        assertEquals(3 * 3000, lines.get().size(), "output far larger than a pipe buffer must be read completely");
    }

    @Test
    void givesUpOnAHungProcessAndKillsIt() {
        var before = aliveChildren();

        var lines = assertTimeoutPreemptively(CALL_BUDGET, () -> ProcessHelper.readWithDeadline(start("hang"), 1_500));

        assertTrue(lines.isEmpty(), "a process killed at the deadline has no trustworthy output");
        assertEquals(before, aliveChildren(), "the hung process must be killed, not leaked");
    }
}
