package com.getpcpanel.util.os;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Minimal kernel32 binding used to attach a debug console at runtime. The native build links as a
 * GUI-subsystem executable (no console window by default); this lets the {@code console} argument
 * open one on demand. See {@link ConsoleSupport}.
 */
public interface Kernel32Console extends StdCallLibrary {
    Kernel32Console INSTANCE = Native.load("kernel32", Kernel32Console.class);

    boolean AllocConsole();
}
