package com.getpcpanel.integration.program.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA binding for {@code DwmGetWindowAttribute} and {@code DwmSetWindowAttribute}, which JNA's bundled libraries omit.
 * {@link WinTopLevelWindows} reads {@code DWMWA_CLOAKED} with it: a cloaked window (a suspended store app, a window on
 * another virtual desktop) counts as visible to {@code IsWindowVisible} but is not on screen. The app window sets
 * {@code DWMWA_USE_IMMERSIVE_DARK_MODE} with it, for a title bar that follows the Windows theme.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}) and registered for
 * reflection (see {@code JnaWin32ReflectionConfig}) plus proxy-config.json.
 */
public interface WinDwmapi extends StdCallLibrary {
    WinDwmapi INSTANCE = Native.load("dwmapi", WinDwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

    int DWMWA_CLOAKED = 14;
    int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    /** Returns an HRESULT; 0 is success. */
    int DwmGetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);

    /** Returns an HRESULT; 0 is success. */
    int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
}
