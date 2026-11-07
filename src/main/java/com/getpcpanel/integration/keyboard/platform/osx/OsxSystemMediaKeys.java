package com.getpcpanel.integration.keyboard.platform.osx;

import com.getpcpanel.integration.keyboard.command.CommandMedia.VolumeButton;
import com.getpcpanel.platform.MacBuild;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import jakarta.enterprise.context.ApplicationScoped;

/** Posts a complete physical-media-key press/release pair to macOS's system media handler. */
@ApplicationScoped
@MacBuild
class OsxSystemMediaKeys {
    private final Events events;

    OsxSystemMediaKeys() {
        this(new CocoaEvents());
    }

    OsxSystemMediaKeys(Events events) {
        this.events = events;
    }

    interface Events {
        Pointer pushPool();
        void popPool(Pointer pool);
        Pointer create(int key, boolean down);
        Pointer cgEvent(Pointer event);
        void post(Pointer event);
    }

    synchronized void send(VolumeButton button) {
        var key = keyCode(button);
        var pool = events.pushPool();
        try {
            var down = events.create(key, true);
            var up = events.create(key, false);
            if (down == null || up == null) {
                throw new IllegalStateException("AppKit could not create a media-key event");
            }
            var downCg = events.cgEvent(down);
            var upCg = events.cgEvent(up);
            if (downCg == null || upCg == null) {
                throw new IllegalStateException("AppKit could not convert a media-key event to CGEvent");
            }
            // Prepare both halves before posting, so a construction failure cannot leave a held key.
            events.post(downCg);
            events.post(upCg);
        } finally {
            // NSEvent and its borrowed CGEvent stay alive through both synchronous CGEventPost calls.
            events.popPool(pool);
        }
    }

    static int keyCode(VolumeButton button) {
        // IOKit/hidsystem/ev_keymap.h: FAST/REWIND are the physical next/previous-track keys.
        return switch (button) {
            case playPause -> 16; // NX_KEYTYPE_PLAY
            case next -> 19;      // NX_KEYTYPE_FAST
            case prev -> 20;      // NX_KEYTYPE_REWIND
            default -> throw new IllegalArgumentException("No system media key for " + button);
        };
    }

    static long keyState(boolean down) {
        return down ? 0xA00L : 0xB00L;
    }

    static long data1(int key, boolean down) {
        return ((long) key << 16) | keyState(down);
    }

    /** NSPoint is two CGFloat/double fields, passed by value on both supported 64-bit architectures. */
    @Structure.FieldOrder({"x", "y"})
    public static class Point extends Structure implements Structure.ByValue {
        public double x;
        public double y;
    }

    interface Objc extends Library {
        Objc INSTANCE = Native.load("objc", Objc.class);

        Pointer objc_getClass(String name);
        Pointer sel_registerName(String selector);
        Pointer objc_msgSend(Pointer receiver, Pointer selector);
        // +otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:
        Pointer objc_msgSend(Pointer receiver, Pointer selector, long type, Point location, long flags,
                double timestamp, long window, Pointer context, short subtype, long data1, long data2);
        Pointer objc_autoreleasePoolPush();
        void objc_autoreleasePoolPop(Pointer pool);
    }

    static class CocoaEvents implements Events {
        // Keep AppKit loaded for the lifetime of the NSEvent class and its Objective-C methods.
        private static final NativeLibrary APP_KIT = NativeLibrary.getInstance("AppKit");

        @Override
        public Pointer pushPool() {
            return Objc.INSTANCE.objc_autoreleasePoolPush();
        }

        @Override
        public void popPool(Pointer pool) {
            Objc.INSTANCE.objc_autoreleasePoolPop(pool);
        }

        @Override
        public Pointer create(int key, boolean down) {
            var objc = Objc.INSTANCE;
            var eventClass = objc.objc_getClass("NSEvent");
            if (eventClass == null) {
                throw new IllegalStateException("AppKit's NSEvent class is unavailable");
            }
            var selector = objc.sel_registerName("otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:");
            return objc.objc_msgSend(eventClass, selector, 14L, new Point(), keyState(down),
                    0d, 0L, null, (short) 8, data1(key, down), -1L);
        }

        @Override
        public Pointer cgEvent(Pointer event) {
            return Objc.INSTANCE.objc_msgSend(event, Objc.INSTANCE.sel_registerName("CGEvent"));
        }

        @Override
        public void post(Pointer event) {
            OsxKeyboard.CoreGraphics.INSTANCE.CGEventPost(OsxKeyboard.K_CG_HID_EVENT_TAP, event);
        }
    }
}
