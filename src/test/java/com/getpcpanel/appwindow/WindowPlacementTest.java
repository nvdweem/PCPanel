package com.getpcpanel.appwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowPlacementTest {
    @TempDir Path dir;

    @Test
    void aSavedPlacementLoadsBack() {
        var placement = new WindowPlacement(-1200, 40, 1600, 1075, true);

        placement.save(dir.resolve("appwindow"));

        assertEquals(placement, WindowPlacement.load(dir.resolve("appwindow")));
    }

    @Test
    void nothingSavedYetOpensAtTheDefault() {
        assertNull(WindowPlacement.load(dir));
    }

    @Test
    void anUnreadableOrEmptyPlacementOpensAtTheDefault() throws IOException {
        Files.writeString(dir.resolve("window.properties"), "width=abc\nheight=10\n");
        assertNull(WindowPlacement.load(dir));

        Files.writeString(dir.resolve("window.properties"), "width=0\nheight=0\n");
        assertNull(WindowPlacement.load(dir));
    }
}
