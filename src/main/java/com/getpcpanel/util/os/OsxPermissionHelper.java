package com.getpcpanel.util.os;

import org.apache.commons.lang3.SystemUtils;

import com.sun.jna.Library;
import com.sun.jna.Native;

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

    private interface ApplicationServicesLib extends Library {
        ApplicationServicesLib INSTANCE = Native.load("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", ApplicationServicesLib.class);

        byte AXIsProcessTrusted(); // Returns the 1-byte mac Boolean, mapping it to a Java boolean is not safe
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
