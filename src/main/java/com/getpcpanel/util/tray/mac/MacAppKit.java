package com.getpcpanel.util.tray.mac;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import javax.annotation.Nullable;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import lombok.extern.log4j.Log4j2;

/**
 * AppKit over the Objective-C runtime, for the menu bar icon: the application object, its delegate, the event loop
 * and the status item. Every AppKit call here must run on the process's first thread ({@link #isMainThread()}).
 *
 * <p>The native image does not link AppKit, so {@link #start} loads it before looking up any class; without that
 * {@code objc_getClass("NSApplication")} is {@code nil} and every message to it silently does nothing.
 *
 * <p>Menu items and the application delegate call back into Java through methods added to a class created at run
 * time ({@value #TARGET_CLASS}). The callbacks only hand over to a {@link Handler} and never let an exception reach
 * Objective-C.
 *
 * <p>Kept apart from the volume feature's {@code ObjcFoundation} on purpose: that bridge carries the CoreAudio
 * process taps, and the menu bar must not be able to change it.
 */
@Log4j2
final class MacAppKit {
    private static final String TARGET_CLASS = "PCPanelMenuBarTarget";
    private static final String ACTION_SELECTOR = "menuItemClicked:";
    private static final long ACTIVATION_POLICY_ACCESSORY = 1;
    private static final double VARIABLE_STATUS_ITEM_LENGTH = -1;
    private static final long EVENT_MASK_ANY = -1L;
    private static final long TERMINATE_LATER = 2;
    /** How long one wait for an event lasts; {@link #stop()} also wakes it at once. */
    private static final double EVENT_WAIT_SECONDS = 1;

    /** Receives what the menu bar and the application delegate report, on the main thread. */
    interface Handler {
        void menuItemClicked(int tag);

        /** The app was opened again (Finder, Spotlight, the Dock, {@code open}) while it runs. */
        void reopen();

        /** macOS asks the app to quit (logout, restart, shutdown, {@code quit app}). */
        void terminateRequested();
    }

    /** One menu entry: its title, the tag reported to {@link Handler#menuItemClicked}, and its key equivalent. */
    record MenuEntry(String title, int tag, String keyEquivalent, boolean separatorBefore) {
    }

    interface Objc extends Library {
        Objc INSTANCE = Native.load("objc", Objc.class);

        Pointer objc_getClass(String name);

        Pointer sel_registerName(String name);

        Pointer objc_allocateClassPair(Pointer superclass, String name, long extraBytes);

        void objc_registerClassPair(Pointer cls);

        boolean class_addMethod(Pointer cls, Pointer selector, Callback imp, String types);

        Pointer objc_msgSend(Pointer receiver, Pointer selector);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, String arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, long arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, byte arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, double arg);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg1, long arg2);

        Pointer objc_msgSend(Pointer receiver, Pointer selector, Pointer arg1, Pointer arg2, Pointer arg3);

        /** {@code nextEventMatchingMask:untilDate:inMode:dequeue:}. */
        Pointer objc_msgSend(Pointer receiver, Pointer selector, long mask, Pointer date, Pointer mode, byte dequeue);

        Pointer objc_autoreleasePoolPush();

        void objc_autoreleasePoolPop(Pointer pool);
    }

    interface LibSystem extends Library {
        LibSystem INSTANCE = Native.load("System", LibSystem.class);

        int pthread_main_np();
    }

    interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);

        Pointer CFRunLoopGetMain();

        void CFRunLoopStop(Pointer runLoop);

        void CFRunLoopWakeUp(Pointer runLoop);
    }

    /** {@code - (void)menuItemClicked:(id)sender}. */
    public interface ActionImp extends Callback {
        void invoke(Pointer self, Pointer cmd, Pointer sender);
    }

    /** {@code - (BOOL)applicationShouldHandleReopen:(NSApplication *)app hasVisibleWindows:(BOOL)flag}. */
    public interface ReopenImp extends Callback {
        byte invoke(Pointer self, Pointer cmd, Pointer app, byte hasVisibleWindows);
    }

    /** {@code - (NSApplicationTerminateReply)applicationShouldTerminate:(NSApplication *)app}. */
    public interface TerminateImp extends Callback {
        long invoke(Pointer self, Pointer cmd, Pointer app);
    }

    // Held for the life of the process: the runtime keeps only the native function pointers, so a collected callback
    // object would leave the class pointing at freed code.
    private static final List<Object> retained = new ArrayList<>();
    private static volatile boolean stopRequested;
    private static @Nullable Pointer app;
    private static @Nullable Pointer target;
    private static @Nullable Pointer runLoopMode;

    private MacAppKit() {
    }

    /** Whether the calling thread is the process's first thread, the only one AppKit runs on. */
    static boolean isMainThread() {
        return LibSystem.INSTANCE.pthread_main_np() == 1;
    }

    /**
     * Creates the application object with {@code handler} as its delegate, without a Dock icon.
     *
     * <p>Creating the application object checks the app in with macOS, which from then on delivers a launch from
     * Finder, Spotlight or the Dock to this process instead of starting another: after that only
     * {@link #runEventLoop()} answers it. So everything that can fail happens first, and this throws only when the
     * app has not checked in; once it has, a later step that fails is logged and the event loop must still run.
     */
    static synchronized void start(Handler handler) {
        NativeLibrary.getInstance("AppKit");
        var mode = NativeLibrary.getInstance("Foundation").getGlobalVariableAddress("NSDefaultRunLoopMode").getPointer(0);
        var targetClass = createTargetClass(handler);
        var created = msg(msg(targetClass, "alloc"), "init");
        if (mode == null || created == null) {
            throw new IllegalStateException("AppKit is unavailable");
        }
        runLoopMode = mode;
        target = created;

        var application = msg(cls("NSApplication"), "sharedApplication");
        if (application == null) {
            throw new IllegalStateException("No application object");
        }
        app = application;
        Pointer pool = null;
        try {
            pool = objc().objc_autoreleasePoolPush();
            msg(application, "setDelegate:", created);
            if (!created.equals(msg(application, "delegate"))) {
                log.error("The application delegate was not accepted; opening PCPanel again will not show the UI");
            }
            objc().objc_msgSend(application, sel("setActivationPolicy:"), ACTIVATION_POLICY_ACCESSORY);
            msg(application, "finishLaunching");
        } catch (Throwable e) {
            log.error("Setting up the application object failed", e);
        } finally {
            if (pool != null) {
                objc().objc_autoreleasePoolPop(pool);
            }
        }
    }

    /** Shows {@code iconPng} (a template image) in the menu bar, with {@code entries} as its menu. */
    static synchronized void addStatusItem(byte[] iconPng, String toolTip, List<MenuEntry> entries) {
        var menuTarget = target;
        if (menuTarget == null) {
            throw new IllegalStateException("AppKit is not started");
        }
        var pool = objc().objc_autoreleasePoolPush();
        try {
            var statusBar = msg(cls("NSStatusBar"), "systemStatusBar");
            var item = objc().objc_msgSend(statusBar, sel("statusItemWithLength:"), VARIABLE_STATUS_ITEM_LENGTH);
            if (item == null) {
                throw new IllegalStateException("No status item");
            }
            msg(item, "retain");
            var button = msg(item, "button");
            var image = image(iconPng);
            objc().objc_msgSend(image, sel("setTemplate:"), (byte) 1);
            msg(button, "setImage:", image);
            msg(button, "setToolTip:", nsString(toolTip));

            var menu = msg(msg(cls("NSMenu"), "alloc"), "init");
            for (var entry : entries) {
                if (entry.separatorBefore()) {
                    msg(menu, "addItem:", msg(cls("NSMenuItem"), "separatorItem"));
                }
                var menuItem = objc().objc_msgSend(msg(cls("NSMenuItem"), "alloc"), sel("initWithTitle:action:keyEquivalent:"),
                        nsString(entry.title()), sel(ACTION_SELECTOR), nsString(entry.keyEquivalent()));
                msg(menuItem, "setTarget:", menuTarget);
                objc().objc_msgSend(menuItem, sel("setTag:"), (long) entry.tag());
                msg(menu, "addItem:", menuItem);
                msg(menuItem, "release");
            }
            msg(item, "setMenu:", menu);
            msg(menu, "release");
        } finally {
            objc().objc_autoreleasePoolPop(pool);
        }
    }

    /**
     * Handles events on the calling (main) thread until {@link #stop()}. A failing iteration is logged and the loop
     * carries on: it is what answers macOS when PCPanel is opened again.
     */
    static void runEventLoop() {
        var application = app;
        var mode = runLoopMode;
        if (application == null || mode == null) {
            throw new IllegalStateException("AppKit is not started");
        }
        var nextEvent = sel("nextEventMatchingMask:untilDate:inMode:dequeue:");
        long failures = 0;
        while (!stopRequested) {
            var pool = objc().objc_autoreleasePoolPush();
            try {
                var until = objc().objc_msgSend(cls("NSDate"), sel("dateWithTimeIntervalSinceNow:"), EVENT_WAIT_SECONDS);
                var event = objc().objc_msgSend(application, nextEvent, EVENT_MASK_ANY, until, mode, (byte) 1);
                if (event != null) {
                    msg(application, "sendEvent:", event);
                }
                failures = 0;
            } catch (Throwable e) {
                failures++;
                if (failures == 1 || failures % 100 == 0) {
                    log.error("Menu bar event loop iteration failed ({} in a row)", failures, e);
                }
                pause();
            } finally {
                objc().objc_autoreleasePoolPop(pool);
            }
        }
    }

    private static void pause() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100)); // unlike sleep, still pauses once interrupted
    }

    /** Ends {@link #runEventLoop()}; safe from any thread. */
    static void stop() {
        stopRequested = true;
        var cf = CoreFoundation.INSTANCE;
        var mainLoop = cf.CFRunLoopGetMain();
        cf.CFRunLoopStop(mainLoop);
        cf.CFRunLoopWakeUp(mainLoop);
    }

    static Pointer createTargetClass(Handler handler) {
        var existing = cls(TARGET_CLASS);
        if (existing != null) {
            throw new IllegalStateException(TARGET_CLASS + " already exists");
        }
        ActionImp action = (self, cmd, sender) -> guard("menu item", () -> handler.menuItemClicked(tag(sender)));
        ReopenImp reopen = (self, cmd, application, visible) -> {
            guard("reopen", handler::reopen);
            return 0; // NO: handled here, AppKit has no window of ours to bring back
        };
        TerminateImp terminate = (self, cmd, application) -> {
            guard("terminate", handler::terminateRequested);
            return TERMINATE_LATER; // never answered: the process exits once its shutdown finishes
        };
        retained.add(action);
        retained.add(reopen);
        retained.add(terminate);

        var objc = objc();
        var targetClass = objc.objc_allocateClassPair(cls("NSObject"), TARGET_CLASS, 0);
        if (targetClass == null) {
            throw new IllegalStateException("Unable to create " + TARGET_CLASS);
        }
        if (!objc.class_addMethod(targetClass, sel(ACTION_SELECTOR), action, "v@:@")
                || !objc.class_addMethod(targetClass, sel("applicationShouldHandleReopen:hasVisibleWindows:"), reopen, "c@:@c")
                || !objc.class_addMethod(targetClass, sel("applicationShouldTerminate:"), terminate, "Q@:@")) {
            throw new IllegalStateException("Unable to add the methods of " + TARGET_CLASS);
        }
        objc.objc_registerClassPair(targetClass);
        return targetClass;
    }

    private static void guard(String what, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            log.error("Menu bar {} callback failed", what, e);
        }
    }

    private static int tag(Pointer sender) {
        return (int) Pointer.nativeValue(msg(sender, "tag"));
    }

    static Pointer image(byte[] png) {
        var bytes = new Memory(png.length);
        bytes.write(0, png, 0, png.length);
        var data = objc().objc_msgSend(cls("NSData"), sel("dataWithBytes:length:"), bytes, (long) png.length);
        var image = msg(msg(cls("NSImage"), "alloc"), "initWithData:", data);
        if (image == null) {
            throw new IllegalStateException("Unable to decode the menu bar icon");
        }
        return msg(image, "autorelease");
    }

    private static Objc objc() {
        return Objc.INSTANCE;
    }

    private static Pointer cls(String name) {
        return objc().objc_getClass(name);
    }

    private static Pointer sel(String name) {
        return objc().sel_registerName(name);
    }

    private static Pointer msg(Pointer receiver, String selector) {
        return objc().objc_msgSend(receiver, sel(selector));
    }

    private static Pointer msg(Pointer receiver, String selector, Pointer arg) {
        return objc().objc_msgSend(receiver, sel(selector), arg);
    }

    private static Pointer nsString(String value) {
        return objc().objc_msgSend(cls("NSString"), sel("stringWithUTF8String:"), value);
    }
}
