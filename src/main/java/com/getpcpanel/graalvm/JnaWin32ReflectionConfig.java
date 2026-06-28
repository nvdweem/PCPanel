package com.getpcpanel.graalvm;

import com.getpcpanel.sleepdetection.Win32Desktop;
import com.getpcpanel.sleepdetection.Win32PowerNotify;
import com.getpcpanel.util.tray.win.WinShell32;
import com.getpcpanel.util.tray.win.WinUser32Ext;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * GraalVM reflection hints for the JNA Win32 window/messaging types.
 *
 * <p>JNA instantiates the declared return type of a native function (and the {@code NativeMapped}
 * fields of a {@link com.sun.jna.platform.win32.WinUser.WNDCLASSEX}-style struct) reflectively via
 * their public no-arg constructor. In a native image those constructors are stripped unless
 * registered, which previously crashed {@code Kernel32.GetModuleHandle} with:
 * <pre>Can't create an instance of class …WinDef$HMODULE, requires a public no-arg constructor</pre>
 *
 * <p>The handle/GDI types used purely for icon extraction (HBITMAP, HDC, BITMAPINFO, …) are already
 * covered by the generated reachability metadata; this class adds the window-creation and
 * window-messaging types used by the power/session helper window and the volume overlay.
 */
@RegisterForReflection(targets = {
        // Project (PCPanel) JNA types — referenced by .class so a package move is compiler-checked
        // rather than silently breaking the native build.
        WinShell32.class,
        WinShell32.NOTIFYICONDATA.class,
        WinUser32Ext.class,
        Win32Desktop.class,
        Win32PowerNotify.class,
}, classNames = {
        "com.sun.jna.platform.win32.WinDef$HMODULE",
        "com.sun.jna.platform.win32.WinDef$HINSTANCE",
        "com.sun.jna.platform.win32.WinDef$HWND",
        "com.sun.jna.platform.win32.WinDef$HMENU",
        "com.sun.jna.platform.win32.WinDef$HICON",
        "com.sun.jna.platform.win32.WinDef$HCURSOR",
        "com.sun.jna.platform.win32.WinDef$HBRUSH",
        "com.sun.jna.platform.win32.WinDef$LRESULT",
        "com.sun.jna.platform.win32.WinDef$WPARAM",
        "com.sun.jna.platform.win32.WinDef$LPARAM",
        "com.sun.jna.platform.win32.WinDef$ATOM",
        "com.sun.jna.platform.win32.WinDef$BOOL",
        "com.sun.jna.platform.win32.WinDef$UINT",
        "com.sun.jna.platform.win32.WinDef$WORD",
        "com.sun.jna.platform.win32.WinDef$DWORD",
        "com.sun.jna.platform.win32.WinDef$LPVOID",
        "com.sun.jna.platform.win32.WinNT$HANDLE",
        "com.sun.jna.platform.win32.WinUser$WNDCLASSEX",
        "com.sun.jna.platform.win32.WinUser$MSG",
        "com.sun.jna.platform.win32.WinDef$POINT",
        "com.sun.jna.platform.win32.WinUser$BLENDFUNCTION",
        // The GUID embedded in the tray NOTIFYICONDATA struct (the struct itself is a .class target above).
        "com.sun.jna.platform.win32.Guid$GUID",
        // The window-procedure callback whose "callback" method JNA invokes reflectively from the
        // native message dispatch (used by Win32PowerNotify, a .class target above).
        "com.sun.jna.platform.win32.WinUser$WindowProc",
        // SendInput keyboard synthesis (CommandMedia media keys, WindowsKeyboard keystrokes): JNA
        // reflectively instantiates the INPUT union, its INPUT_UNION and every member struct to compute
        // the layout, so each needs its fields and no-arg constructor reachable in the native image.
        "com.sun.jna.platform.win32.WinUser$INPUT",
        "com.sun.jna.platform.win32.WinUser$INPUT$INPUT_UNION",
        "com.sun.jna.platform.win32.WinUser$KEYBDINPUT",
        "com.sun.jna.platform.win32.WinUser$MOUSEINPUT",
        "com.sun.jna.platform.win32.WinUser$HARDWAREINPUT",
        // Field types JNA instantiates while sizing the INPUT member structs (WORD/DWORD above cover the
        // rest): LONG for MOUSEINPUT.dx/dy, ULONG_PTR for the dwExtraInfo fields.
        "com.sun.jna.platform.win32.WinDef$LONG",
        "com.sun.jna.platform.win32.BaseTSD$ULONG_PTR",
        // EnumWindows callback for CommandMedia's Spotify-window lookup; JNA builds its CallbackProxy
        // from this concrete type reflectively (CallbackProxy itself is jniAccessible).
        "com.sun.jna.platform.win32.WinUser$WNDENUMPROC",
        // NB: IntByReference (the JNA out-parameter for EnumProcesses/GetWindowThreadProcessId here, and
        // for CoreAudio/X11/keyboard on macOS/Linux) is registered centrally in NativeImageConfig, not
        // here — it is cross-platform, so it must not depend on this Windows-named config.
})
public final class JnaWin32ReflectionConfig {
    private JnaWin32ReflectionConfig() {
    }
}
