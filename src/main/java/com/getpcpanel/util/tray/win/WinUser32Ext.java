package com.getpcpanel.util.tray.win;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * JNA bindings for the Win32 pop-up menu and icon functions that JNA's bundled {@code User32} omits,
 * used by the tray. {@code TrackPopupMenu} is called with {@code TPM_RETURNCMD} so the chosen command
 * is returned directly, which keeps the menu handling inside the existing window procedure.
 *
 * <p>The menu handle is typed as a generic {@link HANDLE} because JNA 5.13 has no {@code HMENU}.
 * Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}).
 */
public interface WinUser32Ext extends StdCallLibrary {
    // DEFAULT_OPTIONS (not UNICODE) so the names map literally: TrackPopupMenu/CreatePopupMenu/DestroyMenu
    // have no W variant. AppendMenuW and LoadIconW are named explicitly and take wide arguments.
    WinUser32Ext INSTANCE = Native.load("user32", WinUser32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

    int MF_STRING = 0x0;
    int MF_DEFAULT = 0x1000; // marks the default (bold) menu item, invoked on a double-click
    int TPM_RIGHTBUTTON = 0x0002;
    int TPM_RETURNCMD = 0x0100;

    int LR_DEFAULTCOLOR = 0x0000;

    int MB_YESNO = 0x00000004;
    int MB_ICONQUESTION = 0x00000020;
    int IDYES = 6;

    /** Native Win32 message box (JNA's bundled {@code User32} omits it). Headless-safe, unlike Swing. */
    int MessageBoxW(HWND hWnd, WString lpText, WString lpCaption, int uType);

    HANDLE CreatePopupMenu();

    boolean AppendMenuW(HANDLE hMenu, int uFlags, int uIDNewItem, WString lpNewItem);

    boolean SetMenuDefaultItem(HANDLE hMenu, int uItem, int fByPos);

    int TrackPopupMenu(HANDLE hMenu, int uFlags, int x, int y, int nReserved, HWND hWnd, Pointer prcRect);

    boolean DestroyMenu(HANDLE hMenu);

    /** {@code lpIconName} is a {@code MAKEINTRESOURCE} pseudo-pointer (e.g. IDI_APPLICATION = 32512). */
    HICON LoadIconW(com.sun.jna.platform.win32.WinDef.HINSTANCE hInstance, Pointer lpIconName);

    /**
     * Creates an icon from the raw image bits of a single {@code .ico} directory entry (a packed DIB or
     * PNG). {@code dwVer} must be {@code 0x00030000}. Returns {@code null} on failure.
     */
    HICON CreateIconFromResourceEx(Pointer presbits, int dwResSize, boolean fIcon, int dwVer,
            int cxDesired, int cyDesired, int uFlags);

    boolean DestroyIcon(HICON hIcon);

    /** Registers the system-wide {@code "TaskbarCreated"} message so the icon can be re-added when Explorer restarts. */
    int RegisterWindowMessageW(WString lpString);
}
