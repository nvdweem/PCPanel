package com.getpcpanel.integration.display.platform.windows;

import javax.annotation.Nullable;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA binding for the DDC/CI monitor functions in {@code dxva2}. Declared here rather than taken from JNA's bundled
 * {@code Dxva2} so handles are plain {@link Pointer}s and the {@code PHYSICAL_MONITOR} array is raw memory: nothing
 * but this interface needs reflection registration in the native image.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}) and registered for
 * reflection (see {@code JnaWin32ReflectionConfig}) plus proxy-config.json.
 */
public interface WinDxva2 extends StdCallLibrary {
    WinDxva2 INSTANCE = Native.load("dxva2", WinDxva2.class);

    /** {@code sizeof(PHYSICAL_MONITOR)}: an 8-byte HANDLE, then a 128-WCHAR description. */
    int PHYSICAL_MONITOR_SIZE = 8 + 128 * 2;

    boolean GetNumberOfPhysicalMonitorsFromHMONITOR(Pointer hMonitor, IntByReference pdwNumberOfPhysicalMonitors);

    /** Fills {@code pPhysicalMonitorArray} with {@code dwPhysicalMonitorArraySize} PHYSICAL_MONITOR entries. */
    boolean GetPhysicalMonitorsFromHMONITOR(Pointer hMonitor, int dwPhysicalMonitorArraySize, Pointer pPhysicalMonitorArray);

    boolean DestroyPhysicalMonitors(int dwPhysicalMonitorArraySize, Pointer pPhysicalMonitorArray);

    boolean GetVCPFeatureAndVCPFeatureReply(Pointer hMonitor, byte bVCPCode, @Nullable Pointer pvct, IntByReference pdwCurrentValue,
            @Nullable IntByReference pdwMaximumValue);

    boolean SetVCPFeature(Pointer hMonitor, byte bVCPCode, int dwNewValue);

    boolean GetCapabilitiesStringLength(Pointer hMonitor, IntByReference pdwCapabilitiesStringLengthInCharacters);

    /** Writes the ASCII capabilities string, terminator included, into {@code pszASCIICapabilitiesString}. */
    boolean CapabilitiesRequestAndCapabilitiesReply(Pointer hMonitor, Pointer pszASCIICapabilitiesString, int dwCapabilitiesStringLengthInCharacters);
}
