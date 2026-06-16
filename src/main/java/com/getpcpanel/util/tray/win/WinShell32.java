package com.getpcpanel.util.tray.win;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.WinDef.HICON;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.win32.W32APITypeMapper;

/**
 * Minimal JNA binding for the Win32 notification-area (system tray) API, which JNA's bundled
 * {@code Shell32} does not expose. Loaded against {@code shell32.dll} with the Unicode options so
 * {@code Shell_NotifyIcon} resolves to {@code Shell_NotifyIconW}.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load} in its
 * static initializer); see the {@code quarkus.native.additional-build-args} list.
 */
public interface WinShell32 extends StdCallLibrary {
    WinShell32 INSTANCE = Native.load("shell32", WinShell32.class, W32APIOptions.UNICODE_OPTIONS);

    int NIM_ADD = 0x0;
    int NIM_MODIFY = 0x1;
    int NIM_DELETE = 0x2;

    int NIF_MESSAGE = 0x1;
    int NIF_ICON = 0x2;
    int NIF_TIP = 0x4;

    boolean Shell_NotifyIcon(int dwMessage, NOTIFYICONDATA lpData);

    /** {@code NOTIFYICONDATAW} (current version: includes the GUID and balloon-icon fields). */
    @Structure.FieldOrder({"cbSize", "hWnd", "uID", "uFlags", "uCallbackMessage", "hIcon", "szTip",
            "dwState", "dwStateMask", "szInfo", "uVersion", "szInfoTitle", "dwInfoFlags", "guidItem", "hBalloonIcon"})
    class NOTIFYICONDATA extends Structure {
        public int cbSize;
        public HWND hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public HICON hIcon;
        public char[] szTip = new char[128];
        public int dwState;
        public int dwStateMask;
        public char[] szInfo = new char[256];
        public int uVersion;
        public char[] szInfoTitle = new char[64];
        public int dwInfoFlags;
        public GUID guidItem;
        public HICON hBalloonIcon;

        public NOTIFYICONDATA() {
            super(W32APITypeMapper.UNICODE);
            cbSize = size();
        }

        public void setTip(String tip) {
            var chars = tip.toCharArray();
            var n = Math.min(chars.length, szTip.length - 1);
            System.arraycopy(chars, 0, szTip, 0, n);
            szTip[n] = 0;
        }
    }
}
