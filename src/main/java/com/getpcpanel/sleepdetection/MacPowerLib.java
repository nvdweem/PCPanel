package com.getpcpanel.sleepdetection;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/** Minimal JNA bindings for IOKit's system power notifications and the CFRunLoop that delivers them. */
public interface MacPowerLib {
    interface IOKit extends Library {
        IOKit INSTANCE = Native.load("/System/Library/Frameworks/IOKit.framework/IOKit", IOKit.class);

        /** {@code void (void *refcon, io_service_t service, natural_t messageType, void *messageArgument)} */
        interface PowerCallback extends Callback {
            @SuppressWarnings("unused") // Arguments dictated by IOKit
            void invoke(Pointer refcon, int service, int messageType, Pointer messageArgument);
        }

        /** Returns the root power domain connection ({@code io_connect_t}), 0 on failure. */
        int IORegisterForSystemPower(Pointer refcon, PointerByReference notifyPort, PowerCallback callback, IntByReference notifier);

        int IODeregisterForSystemPower(IntByReference notifier);

        int IOAllowPowerChange(int rootPort, long notificationId);

        int IOServiceClose(int connect);

        Pointer IONotificationPortGetRunLoopSource(Pointer notifyPort);

        void IONotificationPortDestroy(Pointer notifyPort);
    }

    interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundation.class);

        int kCFStringEncodingUTF8 = 0x08000100;

        Pointer CFRunLoopGetCurrent();

        /** Returns the {@code CFRunLoopRunResult}; {@code 0} for {@code returnAfterSourceHandled} keeps running. */
        int CFRunLoopRunInMode(Pointer mode, double seconds, byte returnAfterSourceHandled);

        void CFRunLoopStop(Pointer runLoop);

        void CFRunLoopAddSource(Pointer runLoop, Pointer source, Pointer mode);

        void CFRunLoopRemoveSource(Pointer runLoop, Pointer source, Pointer mode);

        Pointer CFStringCreateWithCString(Pointer allocator, String cStr, int encoding);

        Pointer CFRetain(Pointer cf);

        void CFRelease(Pointer cf);
    }
}
