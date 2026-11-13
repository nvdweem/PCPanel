package com.getpcpanel.device.provider.pcpanel;

import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.PREFIX_PRO;
import static com.getpcpanel.device.provider.pcpanel.OutputInterpreter.STATIC_COLOR_PRO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.getpcpanel.profile.dto.LightingConfig;

class LightingTraceTest {
    private static final String SERIAL = "ABCDEF123456";
    private static final String MASKED = "panel *3456";
    private static final int WRITTEN = 64;
    private static final int FAILED = -1;

    @TempDir
    Path dir;
    private Path marker;
    private List<String> lines;

    @BeforeEach
    void setUp() {
        marker = dir.resolve(LightingTrace.MARKER_FILE);
        lines = new ArrayList<>();
    }

    private LightingTrace trace() {
        return new LightingTrace(marker, lines::add);
    }

    private static byte[][] frame(String color) {
        return new byte[][] { OutputInterpreter.allColorReport(PREFIX_PRO, STATIC_COLOR_PRO, LightingConfig.createAllColor(color)) };
    }

    @Test
    void offRecordsNothing() {
        var sut = trace();
        sut.requested(SERIAL, "lighting", frame("#000000"));
        sut.written(SERIAL, frame("#000000"), new int[] { FAILED }, "broken");
        sut.device(SERIAL, "connected");
        sut.mark("lights out");

        assertFalse(sut.enabled());
        assertEquals(List.of(), lines);
    }

    @Test
    void theSwitchSurvivesARestartUntilSwitchedOff() {
        trace().setEnabled(true);
        assertTrue(Files.exists(marker));
        assertTrue(trace().enabled(), "a new instance (a restart) is still on");

        trace().setEnabled(false);
        assertFalse(Files.exists(marker));
        assertFalse(trace().enabled());
    }

    @Test
    void aDarkStateIsLabelledWithTheCodeThatSentIt() {
        var sut = trace();
        sut.setEnabled(true);
        sut.requested(SERIAL, "lighting", frame("#000000"));

        var line = lines.getLast();
        assertTrue(line.contains(" lighting DARK "), line);
        assertTrue(line.contains("from LightingTraceTest.aDarkStateIsLabelledWithTheCodeThatSentIt"), line);
        assertTrue(line.contains(MASKED), line);
        assertFalse(lines.stream().anyMatch(l -> l.contains(SERIAL)), "the full serial never appears");
    }

    @Test
    void aRepeatedStateIsCountedNotRepeated() {
        var sut = trace();
        sut.setEnabled(true);
        lines.clear();
        sendRed(sut);
        sendRed(sut);
        sendRed(sut);
        sut.requested(SERIAL, "lighting", frame("#000000"));

        assertEquals(3, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(0).contains(" lighting LIT "), lines.get(0));
        assertTrue(lines.get(1).contains("the previous state 2 more times"), lines.get(1));
        assertTrue(lines.get(2).contains(" lighting DARK "), lines.get(2));
    }

    /** One call site, so every send has the same caller and only the bytes decide whether it repeats. */
    private static void sendRed(LightingTrace sut) {
        sut.requested(SERIAL, "lighting", frame("#ff0000"));
    }

    @Test
    void aFailedWriteAlwaysShows() {
        var sut = trace();
        sut.setEnabled(true);
        lines.clear();
        var red = frame("#ff0000");
        sut.written(SERIAL, red, new int[] { WRITTEN }, null);
        sut.written(SERIAL, red, new int[] { WRITTEN }, null);
        sut.written(SERIAL, red, new int[] { FAILED }, "The device is not connected.");
        sut.written(SERIAL, red, new int[] { FAILED }, "The device is not connected.");

        assertEquals(1, lines.stream().filter(l -> l.endsWith(" ok")).count(), String.join("\n", lines));
        assertEquals(2, lines.stream().filter(l -> l.contains("FAILED at report 1/1 result=-1: The device is not connected.")).count(),
                String.join("\n", lines));
    }

    @Test
    void aMarkSettlesTheRepeatsBeforeIt() {
        var sut = trace();
        sut.setEnabled(true);
        lines.clear();
        sendRed(sut);
        sendRed(sut);
        sut.mark("lights out");

        assertEquals(3, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(1).contains("the previous state 1 more time)"), lines.get(1));
        assertTrue(lines.get(2).endsWith("MARK lights out"), lines.get(2));
    }

    /** In the running app callers reach the trace through Arc's generated proxies; they are the bean they stand for. */
    @Test
    void generatedBeanClassesCountAsTheBean() {
        var pkg = "com.getpcpanel.device.provider.pcpanel.";
        assertEquals(pkg + "OutputInterpreter", LightingTrace.outerClass(pkg + "OutputInterpreter_ClientProxy"));
        assertEquals(pkg + "LightingTrace", LightingTrace.outerClass(pkg + "LightingTrace_Subclass"));
        assertEquals(pkg + "Device", LightingTrace.outerClass(pkg + "Device$$Lambda/0x1234"));
        assertEquals(pkg + "Device", LightingTrace.outerClass(pkg + "Device"));
    }

    @Test
    void linesAreNumberedInOrder() {
        var sut = trace();
        sut.setEnabled(true);
        sut.device(SERIAL, "connected");
        sut.mark("x");

        assertTrue(lines.get(0).startsWith("#1 "), lines.get(0));
        assertTrue(lines.get(1).startsWith("#2 "), lines.get(1));
        assertTrue(lines.get(2).startsWith("#3 "), lines.get(2));
    }
}
