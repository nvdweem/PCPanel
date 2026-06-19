package com.getpcpanel.graalvm;

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
@RegisterForReflection(classNames = {
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
        // System-tray NOTIFYICONDATA struct (and the GUID it embeds): JNA reads the struct's declared
        // fields reflectively to compute its layout. Without this, Structure.getFieldOrder() sees no
        // fields in the native image and the tray fails with "declared field names ([])".
        "com.getpcpanel.util.tray.win.WinShell32$NOTIFYICONDATA",
        "com.sun.jna.platform.win32.Guid$GUID",
        // The tray JNA library interfaces themselves: JNA reads their static INSTANCE field (and maps
        // their methods) reflectively, so the interface types need fields+methods registered.
        "com.getpcpanel.util.tray.win.WinShell32",
        "com.getpcpanel.util.tray.win.WinUser32Ext",
        // Desktop-lock detection helper (OpenInputDesktop/CloseDesktop): same reflective JNA mapping.
        "com.getpcpanel.sleepdetection.Win32Desktop",
        // Display-power detection: the power-notify library and the window-procedure callback whose
        // "callback" method JNA invokes reflectively from the native message dispatch.
        "com.getpcpanel.sleepdetection.Win32PowerNotify",
        "com.sun.jna.platform.win32.WinUser$WindowProc",
        // SendInput keyboard synthesis (media keys in CommandMedia, keystrokes in WindowsKeyboard).
        // JNA reads the INPUT struct + its INPUT_UNION and the union member structs reflectively to
        // compute their layout; without their fields registered, Structure.getFieldOrder() is empty in
        // the native image, so SendInput posts a zeroed INPUT and no key event is ever generated.
        "com.sun.jna.platform.win32.WinUser$INPUT",
        "com.sun.jna.platform.win32.WinUser$INPUT$INPUT_UNION",
        "com.sun.jna.platform.win32.WinUser$KEYBDINPUT",
        "com.sun.jna.platform.win32.WinUser$MOUSEINPUT",
        "com.sun.jna.platform.win32.WinUser$HARDWAREINPUT",
        // EnumWindows callback used by CommandMedia's Spotify path to locate the Spotify window. Like
        // the tray's WindowProc, the concrete callback type must be reflectively registered so JNA can
        // build its CallbackProxy in the native image (CallbackProxy itself is already jniAccessible).
        "com.sun.jna.platform.win32.WinUser$WNDENUMPROC",
})
public final class JnaWin32ReflectionConfig {
    private JnaWin32ReflectionConfig() {
    }
}
