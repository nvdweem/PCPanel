package com.getpcpanel.appwindow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import com.sun.jna.Callback;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * The app window on Linux: a GTK window hosting a WebKitGTK web view, both from the system. Which pair is used is
 * decided here, at run time, from what is installed — WebKitGTK 6.0 on GTK 4, else WebKitGTK 4.1 on GTK 3 — so no
 * build of this application is tied to one of them. The two differ only in how a window is made, filled and run;
 * the web view calls are the same. Without either, the window is unavailable and the application opens the browser.
 *
 * <p>GTK runs on the thread that calls {@link #run}; commands from other threads reach it through
 * {@code g_idle_add}, the one GLib call that may be made from any thread.
 */
final class WebKitGtkWindow implements WindowBackend {
    /** Matches the desktop entry, so the desktop shows the application's name and icon for the window. */
    private static final String APP_ID = "com.getpcpanel.PCPanel";
    private static final String TITLE = "PCPanel";
    private static final int DEFAULT_WIDTH = 1400;
    private static final int DEFAULT_HEIGHT = 900;
    /** The smallest window the UI lays out well in. */
    private static final int MIN_WIDTH = 1300;
    private static final int MIN_HEIGHT = 700;
    private static final int POLICY_NAVIGATION_ACTION = 0;
    private static final int POLICY_NEW_WINDOW_ACTION = 1;

    /** A GTK signal handler with up to three arguments; the ones a signal doesn't pass are ignored. */
    public interface SignalProc extends Callback {
        int invoke(Pointer first, Pointer second, Pointer third);
    }

    /** A GLib {@code GSourceFunc}, as {@code g_idle_add} calls it. */
    public interface SourceProc extends Callback {
        int invoke(Pointer data);
    }

    private record Toolkit(int gtkMajor, NativeLibrary gtk, NativeLibrary webkit) {
    }

    private final Path dataDir;
    private final AtomicReference<String> pendingUrl = new AtomicReference<>();
    private volatile boolean raisePending;
    private volatile boolean closePending;
    private volatile boolean running;

    // Held so the callbacks stay alive while GTK may call them.
    private final SignalProc onDestroy = (a, b, c) -> quit();
    private final SignalProc onCloseRequest = (a, b, c) -> saveSize();
    private final SignalProc onDecidePolicy = this::decidePolicy;
    private final SourceProc onCommands = data -> runCommands();

    // GTK thread only, once run has started.
    private @Nullable Toolkit toolkit;
    private @Nullable NativeLibrary glib;
    private @Nullable NativeLibrary gobject;
    private @Nullable NativeLibrary gio;
    private @Nullable Pointer window;
    private @Nullable Pointer view;
    private @Nullable Pointer loop;
    private String appUrl = "";

    WebKitGtkWindow(Path dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public int run(String url) {
        appUrl = url;
        if (Files.exists(Path.of("/proc/driver/nvidia/version"))) {
            // WebKitGTK's DMA-BUF renderer shows an empty window on many NVIDIA setups; the fallback renders fine.
            Function.getFunction("c", "setenv").invokeInt(new Object[] { "WEBKIT_DISABLE_DMABUF_RENDERER", "1", 0 });
        }
        var kit = loadToolkit();
        if (kit == null) {
            AppWindowMain.log("No WebKitGTK found (libwebkitgtk-6.0 or libwebkit2gtk-4.1)");
            return AppWindowMain.EXIT_UNAVAILABLE;
        }
        toolkit = kit;
        glib = NativeLibrary.getInstance("libglib-2.0.so.0");
        gobject = NativeLibrary.getInstance("libgobject-2.0.so.0");
        gio = NativeLibrary.getInstance("libgio-2.0.so.0");

        call(glib, "g_set_prgname", APP_ID);
        var initialised = kit.gtkMajor() == 4
                ? kit.gtk().getFunction("gtk_init_check").invokeInt(new Object[0])
                : kit.gtk().getFunction("gtk_init_check").invokeInt(new Object[] { null, null });
        if (initialised == 0) {
            AppWindowMain.log("GTK could not open the display");
            return AppWindowMain.EXIT_UNAVAILABLE;
        }

        if (kit.gtkMajor() == 4) {
            loop = glib.getFunction("g_main_loop_new").invokePointer(new Object[] { null, 0 });
        }
        var gtk = kit.gtk();
        window = kit.gtkMajor() == 4
                ? gtk.getFunction("gtk_window_new").invokePointer(new Object[0])
                : gtk.getFunction("gtk_window_new").invokePointer(new Object[] { 0 }); // GTK_WINDOW_TOPLEVEL
        call(gtk, "gtk_window_set_title", window, TITLE);
        call(gtk, "gtk_widget_set_size_request", window, MIN_WIDTH, MIN_HEIGHT);
        var placement = WindowPlacement.load(dataDir);
        call(gtk, "gtk_window_set_default_size", window,
                placement != null ? placement.width() : DEFAULT_WIDTH, placement != null ? placement.height() : DEFAULT_HEIGHT);
        if (placement != null && placement.maximized()) {
            call(gtk, "gtk_window_maximize", window);
        }

        view = kit.webkit().getFunction("webkit_web_view_new").invokePointer(new Object[0]);
        call(gtk, kit.gtkMajor() == 4 ? "gtk_window_set_child" : "gtk_container_add", window, view);
        connect(window, "destroy", onDestroy);
        connect(window, kit.gtkMajor() == 4 ? "close-request" : "delete-event", onCloseRequest);
        connect(view, "decide-policy", onDecidePolicy);
        call(kit.webkit(), "webkit_web_view_load_uri", view, url);
        call(gtk, kit.gtkMajor() == 4 ? "gtk_window_present" : "gtk_widget_show_all", window);

        running = true;
        runCommands();
        if (closePending) {
            return 0; // Closed before it got going; there is no loop left to run.
        }
        if (loop != null) {
            call(glib, "g_main_loop_run", loop);
        } else {
            call(gtk, "gtk_main");
        }
        return 0;
    }

    private static @Nullable Toolkit loadToolkit() {
        // GTK 3 and GTK 4 cannot share a process, so GTK 4 is only loaded once the WebKitGTK built on it is there.
        var webkit6 = tryLoad("libwebkitgtk-6.0.so.4");
        if (webkit6 != null) {
            var gtk4 = tryLoad("libgtk-4.so.1");
            if (gtk4 != null) {
                return new Toolkit(4, gtk4, webkit6);
            }
        }
        var webkit41 = tryLoad("libwebkit2gtk-4.1.so.0");
        if (webkit41 != null) {
            var gtk3 = tryLoad("libgtk-3.so.0");
            if (gtk3 != null) {
                return new Toolkit(3, gtk3, webkit41);
            }
        }
        return null;
    }

    private static @Nullable NativeLibrary tryLoad(String soname) {
        try {
            return NativeLibrary.getInstance(soname);
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
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
        var lib = glib;
        if (running && lib != null) {
            lib.getFunction("g_idle_add").invokeInt(new Object[] { onCommands, null });
        }
    }

    private int runCommands() {
        var kit = toolkit;
        if (kit == null || window == null) {
            return 0;
        }
        if (closePending) {
            call(kit.gtk(), "gtk_window_close", window);
            return 0; // G_SOURCE_REMOVE
        }
        var url = pendingUrl.getAndSet(null);
        if (url != null) {
            call(kit.webkit(), "webkit_web_view_load_uri", view, url);
        }
        if (raisePending) {
            raisePending = false;
            call(kit.gtk(), "gtk_window_present", window);
        }
        return 0;
    }

    /** {@code decide-policy}: a link away from the application, or one meant for a new window, opens in the browser. */
    private int decidePolicy(Pointer webView, Pointer decision, Pointer type) {
        var kind = (int) Pointer.nativeValue(type);
        if (kind != POLICY_NAVIGATION_ACTION && kind != POLICY_NEW_WINDOW_ACTION) {
            return 0;
        }
        var webkit = toolkit.webkit();
        var action = webkit.getFunction("webkit_navigation_policy_decision_get_navigation_action").invokePointer(new Object[] { decision });
        var request = webkit.getFunction("webkit_navigation_action_get_request").invokePointer(new Object[] { action });
        var uri = webkit.getFunction("webkit_uri_request_get_uri").invokeString(new Object[] { request }, false);
        if (Links.sameOrigin(appUrl, uri)) {
            if (kind == POLICY_NAVIGATION_ACTION) {
                return 0; // The application's own page: follow it here.
            }
            call(webkit, "webkit_policy_decision_ignore", decision);
            call(webkit, "webkit_web_view_load_uri", view, uri);
            return 1;
        }
        if (!Links.opensExternally(uri)) {
            return 0;
        }
        call(webkit, "webkit_policy_decision_ignore", decision);
        // GIO hands it to the desktop's default browser (through the portal inside a Flatpak).
        gio.getFunction("g_app_info_launch_default_for_uri").invokeInt(new Object[] { uri, null, null });
        return 1;
    }

    /** Keeps the size for next time; returns false so the window goes on to close. */
    private int saveSize() {
        var gtk = toolkit.gtk();
        var width = new IntByReference();
        var height = new IntByReference();
        call(gtk, toolkit.gtkMajor() == 4 ? "gtk_window_get_default_size" : "gtk_window_get_size", window, width, height);
        var maximized = gtk.getFunction("gtk_window_is_maximized").invokeInt(new Object[] { window }) != 0;
        var previous = WindowPlacement.load(dataDir);
        if (maximized && previous != null) {
            // A maximised window reports the screen's size; keep the size it had before it was maximised.
            new WindowPlacement(0, 0, previous.width(), previous.height(), true).save(dataDir);
        } else if (width.getValue() > 0 && height.getValue() > 0) {
            new WindowPlacement(0, 0, width.getValue(), height.getValue(), maximized).save(dataDir);
        }
        return 0;
    }

    private int quit() {
        running = false;
        if (loop != null) {
            call(glib, "g_main_loop_quit", loop);
        } else {
            call(toolkit.gtk(), "gtk_main_quit");
        }
        return 0;
    }

    private void connect(Pointer instance, String signal, Callback handler) {
        gobject.getFunction("g_signal_connect_data").invokeLong(new Object[] { instance, signal, handler, null, null, 0 });
    }

    private static void call(NativeLibrary library, String function, Object... args) {
        library.getFunction(function).invokeVoid(args);
    }
}
