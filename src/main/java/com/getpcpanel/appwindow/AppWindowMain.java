package com.getpcpanel.appwindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.sun.jna.Platform;

/**
 * Entry point of the app window: the application's own executable started with {@link #ARG}, showing the UI in a
 * window of its own rather than a browser tab. It never boots Quarkus — {@link com.getpcpanel.Main} hands over
 * before anything else runs — so this process is a web view and nothing more, and it ends when its window closes.
 *
 * <p>{@link AppWindowService} drives it over stdin, one command per line. The first line is the URL to open; after
 * that {@code show <url>} opens that URL and raises the window, {@code raise} only raises it and {@code close}
 * closes it. The URL carries the single-use login nonce, which is why it does not travel on the command line. The
 * end of the input means the application is gone, and the window closes with it. Diagnostics go to stderr, which
 * the application writes to {@code logs/appwindow.log}.
 */
public final class AppWindowMain {
    public static final String ARG = "appwindow";
    /** Exit status of a window that cannot be shown on this system, so the application opens the browser instead. */
    public static final int EXIT_UNAVAILABLE = 3;
    private static final int EXIT_USAGE = 2;

    private AppWindowMain() {
    }

    /** {@code args}: the directory the window keeps its own state in (web view profile, size and position). */
    @SuppressWarnings("CallToSystemExit")
    public static void main(String... args) {
        System.exit(run(args));
    }

    static int run(String... args) {
        if (args.length < 1) {
            log("Usage: " + ARG + " <data directory>, with the URL on the first line of stdin");
            return EXIT_USAGE;
        }
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return run(input, () -> createWindow(Path.of(args[0])));
    }

    /** Reads the URL, then runs the window from {@code windows} while a background thread feeds it the commands. */
    static int run(BufferedReader input, Supplier<WindowBackend> windows) {
        String url;
        try {
            url = input.readLine();
        } catch (IOException e) {
            url = null;
        }
        if (url == null || url.isBlank()) {
            log("No URL on stdin");
            return EXIT_USAGE;
        }
        try {
            var window = windows.get();
            if (window == null) {
                log("No app window on this platform");
                return EXIT_UNAVAILABLE;
            }
            var reader = new Thread(() -> readCommands(input, window), "App window input");
            reader.setDaemon(true);
            reader.start();
            return window.run(url);
        } catch (Throwable t) { // NOSONAR - anything that keeps the window from showing means: use the browser
            log("The app window failed: " + t);
            t.printStackTrace();
            return EXIT_UNAVAILABLE;
        }
    }

    private static @Nullable WindowBackend createWindow(Path dataDir) {
        if (Platform.isWindows()) {
            return new WebView2Window(dataDir);
        }
        if (Platform.isLinux()) {
            return new WebKitGtkWindow(dataDir);
        }
        return null;
    }

    static void readCommands(BufferedReader input, WindowBackend window) {
        try {
            String line;
            //noinspection NestedAssignment
            while ((line = input.readLine()) != null) {
                if (line.startsWith("show ")) {
                    window.show(line.substring("show ".length()));
                } else if ("raise".equals(line)) {
                    window.show(null);
                } else if ("close".equals(line)) {
                    window.close();
                }
            }
        } catch (IOException e) {
            log("Lost the connection to the application: " + e);
        }
        window.close();
    }

    static void log(String message) {
        System.err.println(LocalDateTime.now() + " " + message);
    }
}
