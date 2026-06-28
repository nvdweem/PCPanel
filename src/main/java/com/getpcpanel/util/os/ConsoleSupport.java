package com.getpcpanel.util.os;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.sun.jna.Platform;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

/**
 * Attaches a console to the running process on demand. The native Windows build links as a
 * GUI-subsystem executable so no console window appears by default (file logging stays enabled via
 * {@code quarkus.log.file}). Passing the {@value #CONSOLE_ARG} argument opens a console and routes
 * {@code System.out}/{@code System.err} to it, so it must run before Quarkus boots for its log
 * output to be captured too.
 */
@Log4j2
@UtilityClass
public class ConsoleSupport {
    /** Command-line argument that opens a debug console on the native build. */
    public static final String CONSOLE_ARG = "console";

    public void attachConsole() {
        if (!Platform.isWindows()) {
            return; // Other platforms keep their inherited stdout/stderr.
        }
        if (!Kernel32Console.INSTANCE.AllocConsole()) {
            return; // A console is already attached (e.g. launched from a terminal); nothing to do.
        }
        try {
            var out = new PrintStream(new FileOutputStream("CONOUT$"), true, StandardCharsets.UTF_8);
            System.setOut(out);
            System.setErr(out);
        } catch (Exception e) {
            log.warn("Failed to redirect output to attached console", e);
        }
    }
}
