package com.getpcpanel.integration.clipboard.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA bindings for the Win32 global-memory functions the clipboard needs (JNA 5.13's bundled
 * {@code Kernel32} does not expose {@code GlobalAlloc}). {@code dwBytes} is a plain {@code long} — a 64-bit
 * value matching {@code SIZE_T} on the x64 build. Validated by the clipboard round-trip spike.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}), and
 * registered for reflection in {@link com.getpcpanel.graalvm.JnaWin32ReflectionConfig}.
 */
public interface WinClipboardKernel32 extends StdCallLibrary {
    WinClipboardKernel32 INSTANCE = Native.load("kernel32", WinClipboardKernel32.class, W32APIOptions.DEFAULT_OPTIONS);

    int GMEM_MOVEABLE = 0x0002;

    Pointer GlobalAlloc(int uFlags, long dwBytes);

    Pointer GlobalLock(Pointer hMem);

    boolean GlobalUnlock(Pointer hMem);

    Pointer GlobalFree(Pointer hMem);
}
