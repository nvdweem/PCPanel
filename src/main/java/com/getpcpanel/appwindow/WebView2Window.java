package com.getpcpanel.appwindow;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.getpcpanel.util.tray.win.WinAppIcon;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HCURSOR;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WINDOWPLACEMENT;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;
import com.sun.jna.ptr.PointerByReference;

/**
 * The app window on Windows: a plain Win32 window hosting a WebView2 control, the Edge engine that ships with
 * Windows. WebView2 is reached through its COM interfaces by vtable index (the indices are those of
 * {@code WebView2.h}) and Microsoft's {@code WebView2Loader.dll}, which JNA loads from the bundled resources.
 *
 * <p>Everything runs on the thread that calls {@link #run}: WebView2 needs a single-threaded apartment and delivers
 * its results to the handlers through that thread's message loop. Commands from other threads are queued and the
 * window is woken with a posted message.
 */
final class WebView2Window implements WindowBackend, WindowProc {
    private static final String WINDOW_CLASS = "PCPanelAppWindow";
    private static final String TITLE = "PCPanel";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 860;
    /** The UI's own background ({@code --canvas}), shown until the page paints, as COREWEBVIEW2_COLOR (A, R, G, B bytes). */
    private static final int BACKGROUND = 0xFF | 0x0B << 8 | 0x0C << 16 | 0x0F << 24;

    private static final int WM_MOVE = 0x0003;
    private static final int WM_SIZE = 0x0005;
    private static final int WM_SETFOCUS = 0x0007;
    private static final int WM_CLOSE = 0x0010;
    private static final int WM_SETICON = 0x0080;
    private static final int WM_COMMANDS = 0x8000 + 1; // WM_APP + 1
    private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
    private static final int CW_USEDEFAULT = 0x80000000;
    private static final int SW_SHOWNORMAL = 1;
    private static final int SW_SHOWMAXIMIZED = 3;
    private static final int SW_RESTORE = 9;
    private static final int ICON_SMALL = 0;
    private static final int ICON_BIG = 1;
    private static final int IDC_ARROW = 32512;
    private static final int COINIT_APARTMENTTHREADED = 0x2;
    /** DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2: sharp text on every monitor, whatever its scaling. */
    private static final long PER_MONITOR_AWARE_V2 = -4;

    private static final int ENVIRONMENT_CREATE_CONTROLLER = 3;
    private static final int CONTROLLER_PUT_IS_VISIBLE = 4;
    private static final int CONTROLLER_PUT_BOUNDS = 6;
    private static final int CONTROLLER_MOVE_FOCUS = 12;
    private static final int CONTROLLER_NOTIFY_PARENT_MOVED = 23;
    private static final int CONTROLLER_CLOSE = 24;
    private static final int CONTROLLER_GET_WEBVIEW = 25;
    private static final int CONTROLLER2_PUT_BACKGROUND = 27;
    private static final int WEBVIEW_GET_SETTINGS = 3;
    private static final int WEBVIEW_NAVIGATE = 5;
    private static final int WEBVIEW_ADD_NAVIGATION_STARTING = 7;
    private static final int WEBVIEW_ADD_NEW_WINDOW_REQUESTED = 44;
    private static final int SETTINGS_PUT_STATUS_BAR = 10;
    private static final int NAVIGATION_STARTING_GET_URI = 3;
    private static final int NAVIGATION_STARTING_PUT_CANCEL = 8;
    private static final int NEW_WINDOW_GET_URI = 3;
    private static final int NEW_WINDOW_PUT_HANDLED = 6;

    private static final String IID_CONTROLLER2 = "c979903e-d4ca-4228-92eb-47ee3fa96eab";
    private static final String IID_ENVIRONMENT_COMPLETED = "4e8a3389-c9d8-4bd2-b6b5-124fee6cc14d";
    private static final String IID_CONTROLLER_COMPLETED = "6c4819f3-c9b7-4260-8127-c9f5bde7f68c";
    private static final String IID_NAVIGATION_STARTING = "9adbe429-f36d-432b-9ddc-f8881fbd76e3";
    private static final String IID_NEW_WINDOW_REQUESTED = "d4c185fe-c81c-4989-97af-2d3fa7ab5651";

    private final Path dataDir;
    private final ComHandler environmentCompleted = ComHandler.completed(IID_ENVIRONMENT_COMPLETED, this::onEnvironment);
    private final ComHandler controllerCompleted = ComHandler.completed(IID_CONTROLLER_COMPLETED, this::onController);
    private final ComHandler navigationStarting = ComHandler.event(IID_NAVIGATION_STARTING, this::onNavigationStarting);
    private final ComHandler newWindowRequested = ComHandler.event(IID_NEW_WINDOW_REQUESTED, this::onNewWindowRequested);
    private final AtomicReference<String> pendingUrl = new AtomicReference<>();
    private volatile boolean raisePending;
    private volatile boolean closePending;
    private volatile @Nullable HWND hwnd;

    // UI thread only.
    private String appUrl = "";
    private @Nullable WindowPlacement placement;
    private @Nullable Pointer controller;
    private @Nullable Pointer webview;
    private int exitCode;

    WebView2Window(Path dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public int run(String url) {
        appUrl = url;
        placement = WindowPlacement.load(dataDir);
        Function.getFunction("user32", "SetProcessDpiAwarenessContext").invokeInt(new Object[] { new Pointer(PER_MONITOR_AWARE_V2) });
        Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED);

        var window = createWindow();
        if (window == null) {
            return AppWindowMain.EXIT_UNAVAILABLE;
        }
        hwnd = window;
        if (closePending) {
            return 0;
        }

        NativeLibrary loader;
        try {
            loader = NativeLibrary.getInstance("WebView2Loader");
        } catch (UnsatisfiedLinkError e) {
            AppWindowMain.log("WebView2Loader.dll is unavailable: " + e.getMessage());
            return AppWindowMain.EXIT_UNAVAILABLE;
        }
        var userData = new WString(dataDir.resolve("webview2").toString());
        var hr = loader.getFunction("CreateCoreWebView2EnvironmentWithOptions")
                       .invokeInt(new Object[] { null, userData, null, environmentCompleted.pointer() });
        if (hr < 0) {
            AppWindowMain.log("No WebView2 runtime (HRESULT 0x" + Integer.toHexString(hr) + ")");
            return AppWindowMain.EXIT_UNAVAILABLE;
        }

        var msg = new MSG();
        int got;
        //noinspection NestedAssignment
        while ((got = User32.INSTANCE.GetMessage(msg, null, 0, 0)) != 0 && got != -1) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
        return exitCode;
    }

    @Override
    public void show(@Nullable String url) {
        if (url != null) {
            pendingUrl.set(url);
        }
        raisePending = true;
        wake();
    }

    @Override
    public void close() {
        closePending = true;
        wake();
    }

    private void wake() {
        var window = hwnd;
        if (window != null) {
            User32.INSTANCE.PostMessage(window, WM_COMMANDS, new WPARAM(0), new LPARAM(0));
        }
    }

    /** The window, hidden until the web view is ready to show something. */
    private @Nullable HWND createWindow() {
        var user32 = User32.INSTANCE;
        var hInst = Kernel32.INSTANCE.GetModuleHandle(null);
        var wClass = new WNDCLASSEX();
        wClass.hInstance = hInst;
        wClass.lpfnWndProc = this;
        wClass.lpszClassName = WINDOW_CLASS;
        wClass.hCursor = new HCURSOR(
                Function.getFunction("user32", "LoadCursorW").invokePointer(new Object[] { null, new Pointer(IDC_ARROW) }));
        if (user32.RegisterClassEx(wClass).intValue() == 0) {
            AppWindowMain.log("Could not register the window class (error " + Kernel32.INSTANCE.GetLastError() + ")");
            return null;
        }
        var scale = Function.getFunction("user32", "GetDpiForSystem").invokeInt(new Object[0]) / 96.0;
        var window = user32.CreateWindowEx(0, WINDOW_CLASS, TITLE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT,
                (int) (DEFAULT_WIDTH * scale), (int) (DEFAULT_HEIGHT * scale), null, null, hInst, null);
        if (window == null) {
            AppWindowMain.log("Could not create the window (error " + Kernel32.INSTANCE.GetLastError() + ")");
            return null;
        }
        setIcon(window, ICON_SMALL, user32.GetSystemMetrics(WinUser.SM_CXSMICON));
        setIcon(window, ICON_BIG, user32.GetSystemMetrics(WinUser.SM_CXICON));
        return window;
    }

    private static void setIcon(HWND window, int which, int size) {
        var icon = WinAppIcon.load(size > 0 ? size : 32);
        if (icon != null) {
            User32.INSTANCE.SendMessage(window, WM_SETICON, new WPARAM(which), new LPARAM(Pointer.nativeValue(icon.getPointer())));
        }
    }

    private int onEnvironment(Pointer self, int errorCode, Pointer environment) {
        if (errorCode < 0 || environment == null) {
            fail("Could not start WebView2 (HRESULT 0x" + Integer.toHexString(errorCode) + ")");
            return 0;
        }
        var hr = ComHandler.call(environment, ENVIRONMENT_CREATE_CONTROLLER, hwnd, controllerCompleted.pointer());
        if (hr < 0) {
            fail("Could not create the WebView2 control (HRESULT 0x" + Integer.toHexString(hr) + ")");
        }
        return 0;
    }

    private int onController(Pointer self, int errorCode, Pointer created) {
        if (errorCode < 0 || created == null) {
            fail("Could not create the WebView2 control (HRESULT 0x" + Integer.toHexString(errorCode) + ")");
            return 0;
        }
        ComHandler.addRef(created);
        controller = created;
        var view = new PointerByReference();
        ComHandler.call(created, CONTROLLER_GET_WEBVIEW, view);
        webview = view.getValue();
        if (webview == null) {
            fail("The WebView2 control has no web view");
            return 0;
        }
        setBackground(created);
        hideStatusBar(webview);
        ComHandler.call(webview, WEBVIEW_ADD_NAVIGATION_STARTING, navigationStarting.pointer(), new Memory(8));
        ComHandler.call(webview, WEBVIEW_ADD_NEW_WINDOW_REQUESTED, newWindowRequested.pointer(), new Memory(8));
        ComHandler.call(webview, WEBVIEW_NAVIGATE, new WString(appUrl));
        showWindow();
        ComHandler.call(created, CONTROLLER_PUT_IS_VISIBLE, 1);
        resize();
        runCommands();
        return 0;
    }

    private static void setBackground(Pointer controller) {
        var controller2 = new PointerByReference();
        if (ComHandler.call(controller, 0, new GUID(IID_CONTROLLER2), controller2) >= 0 && controller2.getValue() != null) {
            ComHandler.call(controller2.getValue(), CONTROLLER2_PUT_BACKGROUND, BACKGROUND);
            ComHandler.release(controller2.getValue());
        }
    }

    /** No link preview in the corner: this is an app, not a browser. */
    private static void hideStatusBar(Pointer webview) {
        var settings = new PointerByReference();
        if (ComHandler.call(webview, WEBVIEW_GET_SETTINGS, settings) >= 0 && settings.getValue() != null) {
            ComHandler.call(settings.getValue(), SETTINGS_PUT_STATUS_BAR, 0);
            ComHandler.release(settings.getValue());
        }
    }

    /** A link away from the application opens in the browser rather than taking over the window. */
    private int onNavigationStarting(Pointer self, Pointer sender, Pointer args) {
        var uri = readString(args, NAVIGATION_STARTING_GET_URI);
        if (!Links.sameOrigin(appUrl, uri) && Links.opensExternally(uri)) {
            ComHandler.call(args, NAVIGATION_STARTING_PUT_CANCEL, 1);
            openExternally(uri);
        }
        return 0;
    }

    /** A link meant for a new window opens in the browser, or in this window when it is the application's own. */
    private int onNewWindowRequested(Pointer self, Pointer sender, Pointer args) {
        var uri = readString(args, NEW_WINDOW_GET_URI);
        ComHandler.call(args, NEW_WINDOW_PUT_HANDLED, 1);
        if (Links.sameOrigin(appUrl, uri) && webview != null) {
            ComHandler.call(webview, WEBVIEW_NAVIGATE, new WString(uri));
        } else if (Links.opensExternally(uri)) {
            openExternally(uri);
        }
        return 0;
    }

    private static @Nullable String readString(Pointer object, int getter) {
        var value = new PointerByReference();
        if (ComHandler.call(object, getter, value) < 0 || value.getValue() == null) {
            return null;
        }
        try {
            return value.getValue().getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(value.getValue());
        }
    }

    private void openExternally(@Nullable String uri) {
        if (uri != null) {
            Shell32.INSTANCE.ShellExecute(hwnd, "open", uri, null, null, SW_SHOWNORMAL);
        }
    }

    private void showWindow() {
        var window = hwnd;
        var saved = placement;
        if (saved != null) {
            var wp = new WINDOWPLACEMENT();
            wp.showCmd = saved.maximized() ? SW_SHOWMAXIMIZED : SW_SHOWNORMAL;
            wp.rcNormalPosition = new RECT();
            wp.rcNormalPosition.left = saved.x();
            wp.rcNormalPosition.top = saved.y();
            wp.rcNormalPosition.right = saved.x() + saved.width();
            wp.rcNormalPosition.bottom = saved.y() + saved.height();
            // Windows moves a placement that ended up off-screen (a monitor since unplugged) back onto one.
            User32.INSTANCE.SetWindowPlacement(window, wp);
        } else {
            User32.INSTANCE.ShowWindow(window, SW_SHOWNORMAL);
        }
        User32.INSTANCE.SetForegroundWindow(window);
    }

    private void savePlacement() {
        var wp = new WINDOWPLACEMENT();
        if (User32.INSTANCE.GetWindowPlacement(hwnd, wp).booleanValue() && controller != null) {
            var r = wp.rcNormalPosition;
            new WindowPlacement(r.left, r.top, r.right - r.left, r.bottom - r.top, wp.showCmd == SW_SHOWMAXIMIZED).save(dataDir);
        }
    }

    private void resize() {
        if (controller != null) {
            var bounds = new RECT();
            User32.INSTANCE.GetClientRect(hwnd, bounds);
            // put_Bounds takes the RECT by value, which the x64 calling convention passes as a pointer to a copy.
            ComHandler.call(controller, CONTROLLER_PUT_BOUNDS, bounds);
        }
    }

    private void runCommands() {
        if (closePending) {
            User32.INSTANCE.PostMessage(hwnd, WM_CLOSE, new WPARAM(0), new LPARAM(0));
            return;
        }
        if (webview == null) {
            return; // Not showing yet; it runs these once it does.
        }
        var url = pendingUrl.getAndSet(null);
        if (url != null) {
            ComHandler.call(webview, WEBVIEW_NAVIGATE, new WString(url));
        }
        if (raisePending) {
            raisePending = false;
            if (User32.INSTANCE.IsWindowVisible(hwnd) && isMinimized()) {
                User32.INSTANCE.ShowWindow(hwnd, SW_RESTORE);
            }
            User32.INSTANCE.SetForegroundWindow(hwnd);
        }
    }

    private boolean isMinimized() {
        return Function.getFunction("user32", "IsIconic").invokeInt(new Object[] { hwnd }) != 0;
    }

    private void fail(String reason) {
        AppWindowMain.log(reason);
        exitCode = AppWindowMain.EXIT_UNAVAILABLE;
        User32.INSTANCE.DestroyWindow(hwnd);
    }

    @Override
    public LRESULT callback(HWND window, int uMsg, WPARAM wParam, LPARAM lParam) {
        switch (uMsg) {
            case WM_SIZE -> resize();
            case WM_MOVE -> {
                if (controller != null) {
                    ComHandler.call(controller, CONTROLLER_NOTIFY_PARENT_MOVED);
                }
            }
            case WM_SETFOCUS -> {
                if (controller != null) {
                    ComHandler.call(controller, CONTROLLER_MOVE_FOCUS, 0);
                }
            }
            case WM_COMMANDS -> {
                runCommands();
                return new LRESULT(0);
            }
            case WM_CLOSE -> savePlacement();
            case WinUser.WM_DESTROY -> {
                if (controller != null) {
                    ComHandler.call(controller, CONTROLLER_CLOSE);
                }
                User32.INSTANCE.PostQuitMessage(exitCode);
                return new LRESULT(0);
            }
            default -> {
                // Everything else is the default window behaviour.
            }
        }
        return User32.INSTANCE.DefWindowProc(window, uMsg, wParam, lParam);
    }
}
