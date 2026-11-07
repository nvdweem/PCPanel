package com.getpcpanel.util.os;

import org.apache.commons.lang3.SystemUtils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.platform.mac.CoreFoundation;
import com.sun.jna.platform.mac.CoreFoundation.CFBooleanRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFIndex;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;

import lombok.extern.log4j.Log4j2;

/**
 * Checks macOS privacy (TCC) permissions that PCPanel depends on. Both checks are safe-by-default:
 * they return {@code true} on any failure so that we never raise a false alarm, and are only
 * meaningful when running on macOS.
 */
@Log4j2
public final class OsxPermissionHelper {
    private OsxPermissionHelper() {
    }

    private static final String APPLICATION_SERVICES = "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices";
    private static final String CORE_FOUNDATION = "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation";

    private interface ApplicationServicesLib extends Library {
        ApplicationServicesLib INSTANCE = Native.load(APPLICATION_SERVICES, ApplicationServicesLib.class);

        byte AXIsProcessTrusted(); // Returns the 1-byte mac Boolean, mapping it to a Java boolean is not safe

        byte AXIsProcessTrustedWithOptions(CFDictionaryRef options);
    }

    private interface IOKitLib extends Library {
        IOKitLib INSTANCE = Native.load("/System/Library/Frameworks/IOKit.framework/IOKit", IOKitLib.class);

        int kIOHIDRequestTypeListenEvent = 1;
        int kIOHIDAccessTypeGranted = 0;

        int IOHIDCheckAccess(int requestType);
    }

    /**
     * Accessibility permission, required for {@link java.awt.Robot} key injection.
     */
    public static boolean isAccessibilityGranted() {
        if (!SystemUtils.IS_OS_MAC) {
            return true;
        }
        try {
            return ApplicationServicesLib.INSTANCE.AXIsProcessTrusted() != 0;
        } catch (Throwable e) {
            log.debug("Unable to determine Accessibility permission, assuming granted", e);
            return true;
        }
    }

    /**
     * Asks macOS to show its Accessibility prompt (which offers to open the setting and lists PCPanel there) when the
     * permission is missing. Returns whether it is granted, true when that cannot be determined.
     */
    public static boolean requestAccessibility() {
        if (!SystemUtils.IS_OS_MAC) {
            return true;
        }
        try {
            return accessibilityTrusted(true);
        } catch (Throwable e) {
            log.debug("Unable to request Accessibility permission", e);
            return true;
        }
    }

    /** {@code AXIsProcessTrustedWithOptions} with {@code kAXTrustedCheckOptionPrompt}; throws when the call fails. */
    static boolean accessibilityTrusted(boolean prompt) {
        var cf = CoreFoundation.INSTANCE;
        var cfLib = NativeLibrary.getInstance(CORE_FOUNDATION);
        // The key must be AX's own constant: the dictionary compares keys with CFEqual through these callbacks.
        var options = cf.CFDictionaryCreateMutable(null, new CFIndex(1),
                cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
                cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"));
        try {
            var key = new CFStringRef(NativeLibrary.getInstance(APPLICATION_SERVICES).getGlobalVariableAddress("kAXTrustedCheckOptionPrompt").getPointer(0));
            var value = new CFBooleanRef(cfLib.getGlobalVariableAddress(prompt ? "kCFBooleanTrue" : "kCFBooleanFalse").getPointer(0));
            cf.CFDictionarySetValue(options, key, value);
            return ApplicationServicesLib.INSTANCE.AXIsProcessTrustedWithOptions(options) != 0;
        } finally {
            cf.CFRelease(options);
        }
    }

    /**
     * Input Monitoring permission, required to open HID devices such as the PCPanel.
     */
    public static boolean isInputMonitoringGranted() {
        if (!SystemUtils.IS_OS_MAC) {
            return true;
        }
        try { // IOHIDCheckAccess only exists on macOS 10.15+
            return IOKitLib.INSTANCE.IOHIDCheckAccess(IOKitLib.kIOHIDRequestTypeListenEvent) == IOKitLib.kIOHIDAccessTypeGranted;
        } catch (Throwable e) {
            log.debug("Unable to determine Input Monitoring permission, assuming granted", e);
            return true;
        }
    }
}
