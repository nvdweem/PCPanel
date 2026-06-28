package com.getpcpanel.integration.volume.overlay;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.getpcpanel.profile.Save;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.BLENDFUNCTION;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.ptr.PointerByReference;

import lombok.extern.log4j.Log4j2;

/**
 * Win32 layered-window implementation of the volume overlay, used on Windows.
 *
 * <p>The Quarkus/GraalVM native image does not support the AWT windowing toolkit
 * ({@code sun.awt.windows.WToolkit}); showing a Swing {@code JWindow} segfaults the native AWT
 * event loop on Windows. This implementation sidesteps AWT entirely:
 * <ul>
 *   <li>the overlay content is drawn with <em>headless</em> Java2D into a
 *       {@code TYPE_INT_ARGB_PRE} {@link BufferedImage} (fully supported in native image), and</li>
 *   <li>it is presented through a Win32 layered window via JNA
 *       ({@code CreateWindowEx} + {@code UpdateLayeredWindow}).</li>
 * </ul>
 *
 * <p><b>No JNA {@code Callback} is used.</b> JNA callbacks (native→Java upcalls) crash the native
 * image (they segfault in {@code JNIJavaCallTrampolineHolder.varargsJavaCallTrampoline}). So instead
 * of registering a custom window class with a Java window-procedure, the window is created with the
 * built-in {@code "STATIC"} class (whose window procedure lives in {@code user32.dll}). A single
 * dedicated thread owns the window, pumps its message queue with {@code PeekMessage} (dispatched to
 * the native procedure), and performs all show/hide/update work directly as JNA <em>downcalls</em>.
 * The thread parks while the overlay is hidden and is unparked by {@link #show} / the dismiss timer.
 */
@Log4j2
public class Win32VolumeOverlay implements OverlayWindow {
    /** Built-in window class with a native window procedure (avoids a Java callback). */
    private static final String WINDOW_CLASS = "STATIC";
    private static final int DISMISS_MS = 2000;
    private static final long PUMP_INTERVAL_NANOS = 16_000_000L; // ~60 Hz while visible

    // Window styles / flags missing from JNA's WinUser.
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_EX_NOACTIVATE = 0x08000000;
    private static final int ULW_ALPHA = 0x00000002;
    private static final byte AC_SRC_ALPHA = 0x01;
    private static final int PM_REMOVE = 0x0001;

    private final Object lock = new Object();
    private final OverlayRenderer renderer = new OverlayRenderer();
    private final ScheduledExecutorService dismiss = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "PCPanel Overlay Dismiss");
        t.setDaemon(true);
        return t;
    });

    private int width = OverlayRenderer.WIDTH;
    private int height = OverlayRenderer.DEFAULT_HEIGHT;
    private int x;
    private int y = 48;

    private volatile Thread loopThread;
    private volatile boolean running;
    private volatile boolean pendingShow;
    private volatile boolean pendingHide;
    private boolean visible;            // only touched on the loop thread
    private ScheduledFuture<?> dismissFuture;

    @Override
    public void show(OverlayContent content) {
        synchronized (lock) {
            renderer.setValue(Math.round(content.value() * 100f));
            renderer.setIcon(content.icon());
            renderer.setName(content.name());
            renderer.setBarColorOverride(content.barColorCss());
        }
        pendingHide = false;
        pendingShow = true;
        ensureStarted();
        wakeLoop();
        scheduleDismiss();
    }

    @Override
    public void setStyles(Save save) {
        synchronized (lock) {
            height = renderer.setStyles(save);
            width = renderer.width();
        }
    }

    @Override
    public void setLocation(int x, int y) {
        synchronized (lock) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public int getWidth() {
        synchronized (lock) {
            return width;
        }
    }

    @Override
    public int getHeight() {
        synchronized (lock) {
            return height;
        }
    }

    @Override
    public ScreenSize getScreenSize() {
        var user32 = User32.INSTANCE;
        return new ScreenSize(user32.GetSystemMetrics(WinUser.SM_CXSCREEN), user32.GetSystemMetrics(WinUser.SM_CYSCREEN));
    }

    private void ensureStarted() {
        synchronized (lock) {
            if (loopThread != null) {
                return;
            }
            var thread = new Thread(this::runLoop, "PCPanel Overlay");
            thread.setDaemon(true);
            loopThread = thread;
            thread.start();
        }
    }

    private void wakeLoop() {
        var t = loopThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    private void scheduleDismiss() {
        synchronized (lock) {
            if (dismissFuture != null) {
                dismissFuture.cancel(false);
            }
            dismissFuture = dismiss.schedule(() -> {
                pendingHide = true;
                wakeLoop();
            }, DISMISS_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void runLoop() {
        var user32 = User32.INSTANCE;
        HMODULE hInst = Kernel32.INSTANCE.GetModuleHandle(null);

        int initialWidth;
        int initialHeight;
        synchronized (lock) {
            initialWidth = width;
            initialHeight = height;
        }

        var exStyle = WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT | User32.WS_EX_TOPMOST
                | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE;
        var hWnd = user32.CreateWindowEx(exStyle, WINDOW_CLASS, "PCPanel Volume Overlay",
                WinUser.WS_POPUP, 0, 0, initialWidth, initialHeight, null, null, hInst, null);
        if (hWnd == null) {
            log.error("Unable to create overlay window (error {})", Kernel32.INSTANCE.GetLastError());
            return;
        }

        running = true;
        var msg = new MSG();
        try {
            while (running) {
                while (user32.PeekMessage(msg, null, 0, 0, PM_REMOVE)) {
                    user32.TranslateMessage(msg);
                    user32.DispatchMessage(msg);
                }
                if (pendingShow) {
                    pendingShow = false;
                    doShow(hWnd);
                }
                if (pendingHide) {
                    pendingHide = false;
                    doHide(hWnd);
                }
                if (visible) {
                    LockSupport.parkNanos(PUMP_INTERVAL_NANOS);
                } else {
                    LockSupport.park();
                }
            }
        } catch (RuntimeException e) {
            log.error("Overlay loop terminated", e);
        }
    }

    /** Renders the current state into a layered-window bitmap. Runs on the loop thread. */
    private void doShow(HWND hWnd) {
        int w;
        int ht;
        int posX;
        int posY;
        synchronized (lock) {
            w = width;
            ht = height;
            posX = x;
            posY = y;
        }

        try {
            // Render and present OUTSIDE the lock: show() runs on the HID input thread and takes the same
            // lock, so a slow render must never hold it. And catch Throwable: the AWT/Java2D image pipeline
            // is fragile in the native image (e.g. BufferedImage class init can fail to load native awt), so
            // a failure here must only skip a frame, never kill this thread.
            var image = new BufferedImage(w, ht, BufferedImage.TYPE_INT_ARGB_PRE);
            var g2 = image.createGraphics();
            try {
                renderer.render(g2, w, ht);
            } finally {
                g2.dispose();
            }
            pushBitmap(hWnd, image, w, ht, posX, posY);
            if (!visible) {
                User32.INSTANCE.ShowWindow(hWnd, WinUser.SW_SHOWNA);
                visible = true;
            }
        } catch (Throwable t) {
            log.warn("Overlay render/show failed; skipping frame", t);
        }
    }

    private void doHide(HWND hWnd) {
        if (visible) {
            User32.INSTANCE.ShowWindow(hWnd, WinUser.SW_HIDE);
            visible = false;
        }
    }

    /** Pushes the premultiplied-ARGB image into the layered window via {@code UpdateLayeredWindow}. */
    private void pushBitmap(HWND hWnd, BufferedImage image, int w, int ht, int posX, int posY) {
        var user32 = User32.INSTANCE;
        var gdi32 = GDI32.INSTANCE;

        HDC screenDC = user32.GetDC(null);
        HDC memDC = gdi32.CreateCompatibleDC(screenDC);
        HBITMAP bitmap = null;
        HANDLE oldBitmap = null;
        try {
            var bmi = new BITMAPINFO();
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -ht;            // negative → top-down DIB (matches BufferedImage row order)
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

            var ppvBits = new PointerByReference();
            bitmap = gdi32.CreateDIBSection(memDC, bmi, WinGDI.DIB_RGB_COLORS, ppvBits, null, 0);
            if (bitmap == null) {
                log.warn("CreateDIBSection failed (error {})", Kernel32.INSTANCE.GetLastError());
                return;
            }

            var pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            Pointer bits = ppvBits.getValue();
            // Java TYPE_INT_ARGB_PRE int 0xAARRGGBB → little-endian bytes B,G,R,A, which is exactly the
            // premultiplied BGRA layout Windows expects for ULW_ALPHA.
            bits.write(0, pixels, 0, pixels.length);

            oldBitmap = gdi32.SelectObject(memDC, bitmap);

            var blend = new BLENDFUNCTION();
            blend.SourceConstantAlpha = (byte) 0xFF;
            blend.AlphaFormat = AC_SRC_ALPHA;

            var dst = new POINT(posX, posY);
            var src = new POINT(0, 0);
            var size = new SIZE(w, ht);
            var ok = user32.UpdateLayeredWindow(hWnd, screenDC, dst, size, memDC, src, 0, blend, ULW_ALPHA);
            if (!ok) {
                log.warn("UpdateLayeredWindow failed (error {})", Kernel32.INSTANCE.GetLastError());
            }
        } finally {
            if (oldBitmap != null) {
                gdi32.SelectObject(memDC, oldBitmap);
            }
            if (bitmap != null) {
                gdi32.DeleteObject(bitmap);
            }
            gdi32.DeleteDC(memDC);
            user32.ReleaseDC(null, screenDC);
        }
    }
}
