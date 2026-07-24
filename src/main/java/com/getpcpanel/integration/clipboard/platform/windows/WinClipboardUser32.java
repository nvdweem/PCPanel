package com.getpcpanel.integration.clipboard.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA bindings for the Win32 clipboard functions that JNA's bundled {@code User32} omits (validated
 * against JNA 5.13 with a round-trip spike). No AWT is involved, so this works in the Windows native
 * image exactly like the tray's {@link com.getpcpanel.util.tray.win.WinUser32Ext}.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}), and
 * registered for reflection in {@link com.getpcpanel.graalvm.JnaWin32ReflectionConfig}.
 */
public interface WinClipboardUser32 extends StdCallLibrary {
    // DEFAULT_OPTIONS: these functions have no A/W variants, so the names map literally.
    WinClipboardUser32 INSTANCE = Native.load("user32", WinClipboardUser32.class, W32APIOptions.DEFAULT_OPTIONS);

    /** Clipboard format for a NUL-terminated UTF-16LE string. */
    int CF_UNICODETEXT = 13;

    boolean OpenClipboard(Pointer hWndNewOwner);

    boolean EmptyClipboard();

    /** Takes ownership of {@code hMem} (a GMEM_MOVEABLE HGLOBAL) on success; the caller must not free it. */
    Pointer SetClipboardData(int uFormat, Pointer hMem);

    boolean CloseClipboard();
}
