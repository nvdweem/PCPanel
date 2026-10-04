package com.getpcpanel.integration.display.platform.windows;

import javax.annotation.Nullable;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/**
 * JNA binding for the {@code user32} display functions that JNA's bundled {@code User32} omits. {@link WinDisplayPower}
 * finds each monitor's {@code HMONITOR} with them instead of {@code EnumDisplayMonitors}, whose callback the native
 * image cannot run, and its model name with the display configuration calls. Structures are passed as raw memory at their documented offsets, and handles come back as plain
 * {@link Pointer}s, so nothing but this interface needs reflection registration.
 *
 * <p>Must be {@code --initialize-at-run-time} in the native image (it calls {@code Native.load}) and registered for
 * reflection (see {@code JnaWin32ReflectionConfig}) plus proxy-config.json.
 */
public interface WinDisplayUser32 extends StdCallLibrary {
    WinDisplayUser32 INSTANCE = Native.load("user32", WinDisplayUser32.class);

    /** {@code sizeof(DISPLAY_DEVICEW)}: cb, DeviceName[32], DeviceString[128], StateFlags, DeviceID[128], DeviceKey[128]. */
    int DISPLAY_DEVICE_SIZE = 840;
    int DISPLAY_DEVICE_NAME_OFFSET = 4;
    int DISPLAY_DEVICE_STATE_FLAGS_OFFSET = 324;
    int DISPLAY_DEVICE_ID_OFFSET = 328;
    /** An adapter output that is part of the desktop. */
    int DISPLAY_DEVICE_ATTACHED_TO_DESKTOP = 0x1;
    /** The output that holds the main display. */
    int DISPLAY_DEVICE_PRIMARY_DEVICE = 0x4;
    /** A monitor that is in use. */
    int DISPLAY_DEVICE_ACTIVE = 0x1;
    /** Fills DeviceID with the monitor's device interface path rather than its hardware id. */
    int EDD_GET_DEVICE_INTERFACE_NAME = 0x1;

    /** {@code sizeof(DEVMODEW)}; dmSize is a WORD at 68, dmPosition a POINTL at 76, dmPelsWidth/Height DWORDs at 172/176. */
    int DEVMODE_SIZE = 220;
    int DEVMODE_SIZE_OFFSET = 68;
    int DEVMODE_POSITION_OFFSET = 76;
    int DEVMODE_PELS_WIDTH_OFFSET = 172;
    int DEVMODE_PELS_HEIGHT_OFFSET = 176;
    int ENUM_CURRENT_SETTINGS = -1;

    int MONITOR_DEFAULTTONULL = 0;

    /** {@code lpDevice} null lists the adapter outputs; an output's DeviceName lists the monitors on it. */
    boolean EnumDisplayDevicesW(@Nullable WString lpDevice, int iDevNum, Pointer lpDisplayDevice, int dwFlags);

    boolean EnumDisplaySettingsExW(WString lpszDeviceName, int iModeNum, Pointer lpDevMode, int dwFlags);

    /** {@code lprc}: a RECT (left, top, right, bottom). Returns the HMONITOR, or null. */
    @Nullable Pointer MonitorFromRect(Pointer lprc, int dwFlags);

    /** The paths that are in use; the only {@code QueryDisplayConfig} flag that takes no topology argument. */
    int QDC_ONLY_ACTIVE_PATHS = 0x2;
    int ERROR_SUCCESS = 0;
    int ERROR_INSUFFICIENT_BUFFER = 122;
    /** {@code sizeof(DISPLAYCONFIG_PATH_INFO)}: a 20-byte source info, a 48-byte target info, then flags. */
    int PATH_INFO_SIZE = 72;
    /** {@code targetInfo.adapterId} (a LUID) and {@code targetInfo.id} within a DISPLAYCONFIG_PATH_INFO. */
    int PATH_TARGET_ADAPTER_OFFSET = 20;
    int PATH_TARGET_ID_OFFSET = 28;
    /** {@code sizeof(DISPLAYCONFIG_MODE_INFO)}. */
    int MODE_INFO_SIZE = 64;

    /** {@code DISPLAYCONFIG_DEVICE_INFO_GET_TARGET_NAME}. */
    int DEVICE_INFO_GET_TARGET_NAME = 2;
    /**
     * {@code sizeof(DISPLAYCONFIG_TARGET_DEVICE_NAME)}: the header (type, size, adapterId LUID, id), flags,
     * outputTechnology, two EDID WORDs, connectorInstance, monitorFriendlyDeviceName[64], monitorDevicePath[128].
     */
    int TARGET_NAME_SIZE = 420;
    int DEVICE_INFO_TYPE_OFFSET = 0;
    int DEVICE_INFO_SIZE_OFFSET = 4;
    int DEVICE_INFO_ADAPTER_OFFSET = 8;
    int DEVICE_INFO_ID_OFFSET = 16;
    int TARGET_NAME_FRIENDLY_OFFSET = 36;
    int TARGET_NAME_PATH_OFFSET = 164;

    /** Returns a Win32 error code; ERROR_SUCCESS when the counts were written. */
    int GetDisplayConfigBufferSizes(int flags, IntByReference numPathArrayElements, IntByReference numModeInfoArrayElements);

    /** {@code currentTopologyId} must be null for {@link #QDC_ONLY_ACTIVE_PATHS}. Returns a Win32 error code. */
    int QueryDisplayConfig(int flags, IntByReference numPathArrayElements, Pointer pathArray, IntByReference numModeInfoArrayElements,
            Pointer modeInfoArray, @Nullable Pointer currentTopologyId);

    /** {@code requestPacket} starts with a DISPLAYCONFIG_DEVICE_INFO_HEADER naming what to fill. Returns a Win32 error code. */
    int DisplayConfigGetDeviceInfo(Pointer requestPacket);
}
