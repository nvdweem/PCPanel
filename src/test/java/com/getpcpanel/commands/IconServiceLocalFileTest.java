package com.getpcpanel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link IconService#localIconFile(String)}: which overlay-icon values name a local file (a v1
 * absolute path or a {@code file:} URL) rather than a process name or a bundled asset.
 */
class IconServiceLocalFileTest {
    @TempDir Path dir;

    @Test
    void absolutePathToAnExistingFile() throws IOException {
        var icon = Files.createFile(dir.resolve("mic.png"));
        assertEquals(icon.toFile(), IconService.localIconFile(icon.toString()));
    }

    @Test
    void fileUrlToAnExistingFile() throws IOException {
        var icon = Files.createFile(dir.resolve("mic icon.png"));
        assertEquals(icon.toFile(), IconService.localIconFile(icon.toUri().toString()));
    }

    @Test
    void missingFileIsNotAnIconFile() {
        assertNull(IconService.localIconFile(dir.resolve("missing.png").toString()));
    }

    @Test
    void directoryIsNotAnIconFile() {
        assertNull(IconService.localIconFile(dir.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "spotify.exe", "Spotify", "/assets/icons/mic.png", "file:not a url", "http://example.com/mic.png" })
    void otherValuesAreNotIconFiles(String value) {
        assertNull(IconService.localIconFile(value));
    }
}
