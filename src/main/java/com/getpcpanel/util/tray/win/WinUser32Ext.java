package com.getpcpanel.util.tray.win;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA bindings for the Win32 pop-up menu functions that JNA's bundled {@code User32} omits, used to
 * show the tray's right-click "Exit" menu. {@code TrackPopupMenu} is called with {@code TPM_RETURNCMD}
 * so the chosen command is returned directly — no {@code WM_COMMAND} window procedure (and therefore
 * no JNA {@code Callback}, which crashes the native image) is required.
 *
 * <p>The menu handle is typed as a generic {@link HANDLE} because JNA 5.13 has no {@code HMENU}.
 * Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}).
 */
public interface WinUser32Ext extends StdCallLibrary {
    // DEFAULT_OPTIONS (not UNICODE) so the names map literally: TrackPopupMenu/CreatePopupMenu/DestroyMenu
    // have no W variant. AppendMenuW and LoadIconW are named explicitly and take wide arguments.
    WinUser32Ext INSTANCE = Native.load("user32", WinUser32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

    int MF_STRING = 0x0;
    int TPM_RIGHTBUTTON = 0x0002;
    int TPM_RETURNCMD = 0x0100;

    HANDLE CreatePopupMenu();

    boolean AppendMenuW(HANDLE hMenu, int uFlags, int uIDNewItem, WString lpNewItem);

    int TrackPopupMenu(HANDLE hMenu, int uFlags, int x, int y, int nReserved, HWND hWnd, Pointer prcRect);

    boolean DestroyMenu(HANDLE hMenu);

    /** {@code lpIconName} is a {@code MAKEINTRESOURCE} pseudo-pointer (e.g. IDI_APPLICATION = 32512). */
    com.sun.jna.platform.win32.WinDef.HICON LoadIconW(com.sun.jna.platform.win32.WinDef.HINSTANCE hInstance, Pointer lpIconName);
}
