package com.getpcpanel.appwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

class AppWindowMainTest {
    @Test
    void theFirstLineIsTheUrlTheWindowOpens() {
        var window = new RecordingWindow();

        var status = AppWindowMain.run(input("http://localhost:7654/api/auth/bootstrap?nonce=n\n"), () -> window);

        assertEquals(0, status);
        assertEquals("http://localhost:7654/api/auth/bootstrap?nonce=n", window.openedUrl);
    }

    @Test
    void withoutAUrlNoWindowOpens() {
        var window = new RecordingWindow();

        var status = AppWindowMain.run(input(""), () -> window);

        assertEquals(2, status);
        assertEquals(null, window.openedUrl);
    }

    @Test
    void aPlatformWithoutAWindowReportsItUnavailable() {
        assertEquals(AppWindowMain.EXIT_UNAVAILABLE, AppWindowMain.run(input("http://localhost/\n"), () -> null));
    }

    @Test
    void commandsReachTheWindowAndTheEndOfInputClosesIt() {
        var window = new RecordingWindow();

        AppWindowMain.readCommands(input("show http://localhost/?report=1\nraise\nbogus\nclose\n"), window);

        assertEquals(List.of("show http://localhost/?report=1", "show null", "close", "close"), window.calls);
    }

    private static BufferedReader input(String text) {
        return new BufferedReader(new StringReader(text));
    }

    private static final class RecordingWindow implements WindowBackend {
        private final List<String> calls = new ArrayList<>();
        private @Nullable String openedUrl;

        @Override
        public int run(String url) {
            openedUrl = url;
            return 0;
        }

        @Override
        public void show(@Nullable String url) {
            calls.add("show " + url);
        }

        @Override
        public void close() {
            calls.add("close");
        }
    }
}
