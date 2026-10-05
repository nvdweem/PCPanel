package com.getpcpanel.appwindow;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.annotation.Nullable;

/**
 * Where the app window was when it last closed, so it opens there again. Coordinates are in the platform's own
 * units (physical pixels on Windows, logical on GTK, where only the size is kept: Wayland has no window position).
 */
record WindowPlacement(int x, int y, int width, int height, boolean maximized) {
    private static final String FILE = "window.properties";

    static @Nullable WindowPlacement load(Path dataDir) {
        var props = new Properties();
        try (Reader reader = Files.newBufferedReader(dataDir.resolve(FILE), StandardCharsets.UTF_8)) {
            props.load(reader);
            var placement = new WindowPlacement(
                    Integer.parseInt(props.getProperty("x", "0")),
                    Integer.parseInt(props.getProperty("y", "0")),
                    Integer.parseInt(props.getProperty("width")),
                    Integer.parseInt(props.getProperty("height")),
                    Boolean.parseBoolean(props.getProperty("maximized")));
            return placement.width() > 0 && placement.height() > 0 ? placement : null;
        } catch (IOException | RuntimeException e) {
            return null; // None saved yet, or unreadable: open at the default size.
        }
    }

    void save(Path dataDir) {
        var props = new Properties();
        props.setProperty("x", Integer.toString(x));
        props.setProperty("y", Integer.toString(y));
        props.setProperty("width", Integer.toString(width));
        props.setProperty("height", Integer.toString(height));
        props.setProperty("maximized", Boolean.toString(maximized));
        try {
            Files.createDirectories(dataDir);
            try (Writer writer = Files.newBufferedWriter(dataDir.resolve(FILE), StandardCharsets.UTF_8)) {
                props.store(writer, null);
            }
        } catch (IOException e) {
            AppWindowMain.log("Could not save the window placement: " + e);
        }
    }
}
