package com.getpcpanel.cpp.linux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.jna.Library;
import com.sun.jna.Platform;

/**
 * Exercises the Linux X11/XTest JNA keystroke path so the GraalVM tracing agent records the
 * dynamic proxies that {@code Native.load} builds for the {@code X11}/{@code XTest}
 * {@link Library} interfaces. Without those proxy registrations the native image throws
 * {@code MissingReflectionRegistrationError} on the first keystroke (the bug this addresses).
 *
 * <p>The proxy is created when the interface class initialises ({@code Native.load} runs in its
 * static initialiser) — BEFORE any X call. This test therefore force-initialises {@code X11} and
 * {@code XTest} to trigger the loads, and deliberately does NOT call {@code executeKeyStroke}: on a
 * machine with a live display that would inject real keystrokes into the focused window. The
 * keysym/parsing logic of {@code executeKeyStroke} is covered by its early-return guards only.
 *
 * <p>On a host without {@code libX11}/{@code libXtst} (e.g. headless CI) the loads fail and the
 * test is skipped rather than failed; the metadata is then supplied by a host that does have them.
 */
@DisplayName("Linux keyboard native-image config coverage")
class LinuxKeyboardNativeConfigTest {

    private static final String X11 = "com.getpcpanel.cpp.linux.LinuxKeyboard$X11";
    private static final String XTEST = "com.getpcpanel.cpp.linux.LinuxKeyboard$XTest";

    @Test
    @DisplayName("X11 is a JNA Library interface declaring XOpenDisplay/XFlush/XKeysymToKeycode")
    void x11InterfaceShape() throws Exception {
        var clazz = Class.forName(X11);
        assertTrue(clazz.isInterface());
        assertTrue(Library.class.isAssignableFrom(clazz), "JNA proxy requires it to be a Library");
        assertTrue(hasMethod(clazz, "XOpenDisplay"));
        assertTrue(hasMethod(clazz, "XFlush"));
        assertTrue(hasMethod(clazz, "XKeysymToKeycode"));
    }

    @Test
    @DisplayName("XTest is a JNA Library interface declaring XTestFakeKeyEvent")
    void xTestInterfaceShape() throws Exception {
        var clazz = Class.forName(XTEST);
        assertTrue(clazz.isInterface());
        assertTrue(Library.class.isAssignableFrom(clazz), "JNA proxy requires it to be a Library");
        assertTrue(hasMethod(clazz, "XTestFakeKeyEvent"));
    }

    /**
     * Loads {@code libX11}/{@code libXtst} through JNA, building the dynamic proxies the native image
     * needs. Under the tracing agent this is what records them in {@code reachability-metadata.json}.
     */
    @Test
    @DisplayName("Native.load builds the X11 and XTest JNA proxies (records them under the tracing agent)")
    void nativeLoadBuildsProxies() {
        assumeTrue(Platform.isLinux(), "X11/XTest keystroke injection is Linux-only");
        var loader = LinuxKeyboardNativeConfigTest.class.getClassLoader();
        try {
            // initialize=true runs the static initialiser: `INSTANCE = Native.load(...)` -> Proxy created.
            Class.forName(X11, true, loader);
            Class.forName(XTEST, true, loader);
        } catch (Throwable e) {
            assumeTrue(false, "libX11/libXtst not available on this host; skipping (metadata comes from a host that has them): " + e);
        }
    }

    @Test
    @DisplayName("executeKeyStroke ignores null/UNDEFINED input without touching the display")
    void executeKeyStrokeGuardsAreSafe() {
        assertDoesNotThrow(() -> {
            LinuxKeyboard.executeKeyStroke(null);
            LinuxKeyboard.executeKeyStroke("ctrl+UNDEFINED");
        });
    }

    private static boolean hasMethod(Class<?> clazz, String name) {
        for (var m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
