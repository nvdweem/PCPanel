package com.getpcpanel.integration.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenOrRunTest {
    @TempDir Path dir;
    private RecordingPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RecordingPlatform();
    }

    @Test
    void websitesOpenInTheDefaultApp() {
        platform.openOrRun("https://example.com");
        platform.openOrRun("steam://rungameid/570");

        assertEquals(List.of("open https://example.com", "open steam://rungameid/570"), platform.calls);
    }

    @Test
    void linksWithoutSlashesOpenInTheDefaultApp() {
        platform.openOrRun("mailto:me@example.com");
        platform.openOrRun("ms-settings:sound");
        platform.openOrRun("spotify:track:4uLU6hMCjMI75M1A2tKUQC");

        assertEquals(List.of("open mailto:me@example.com", "open ms-settings:sound", "open spotify:track:4uLU6hMCjMI75M1A2tKUQC"), platform.calls);
    }

    @Test
    void aDrivePathIsNotALink() {
        platform.openOrRun("C:\\Missing\\tool.exe --flag");
        platform.openOrRun("D:/Missing/tool.exe");

        assertEquals(List.of("exec C:\\Missing\\tool.exe --flag", "exec D:/Missing/tool.exe"), platform.calls);
    }

    @Test
    void foldersAndDocumentsOpenInTheDefaultApp() throws IOException {
        var document = Files.writeString(dir.resolve("notes.txt"), "x");

        platform.openOrRun(dir.toString());
        platform.openOrRun(document.toString());

        assertEquals(List.of("open " + dir, "open " + document), platform.calls);
    }

    @Test
    void programsAndCommandLinesRunAsBefore() throws IOException {
        var program = Files.writeString(dir.resolve("tool.exe"), "x");

        platform.openOrRun(program.toString());
        platform.openOrRun("notepad.exe C:\\notes.txt");

        assertEquals(List.of("exec " + program, "exec notepad.exe C:\\notes.txt"), platform.calls);
    }

    private static final class RecordingPlatform extends IPlatformCommand {
        final List<String> calls = new ArrayList<>();

        @Override
        public void exec(String shortcut) {
            calls.add("exec " + shortcut);
        }

        @Override
        public void open(String target) {
            calls.add("open " + target);
        }

        @Override
        public void kill(String process) {
        }

        @Override
        protected boolean isExecutable(File file) {
            return file.getName().endsWith(".exe");
        }
    }
}
