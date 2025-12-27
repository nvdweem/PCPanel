package com.getpcpanel.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HMONITOR;

public final class MonitorNativeUtil {
    private MonitorNativeUtil() {
    }

    public static List<MonitorInfo> listMonitors() {
        var result = new ArrayList<MonitorInfo>();
        var index = new AtomicInteger(0);
        User32.INSTANCE.EnumDisplayMonitors(null, null, (hMonitor, hdc, rect, lparam) -> {
            var count = new IntByReference();
            if (Dxva2.INSTANCE.GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor, count)) {
                var monitorCount = count.getValue();
                var monitors = (PHYSICAL_MONITOR[]) new PHYSICAL_MONITOR().toArray(monitorCount);
                if (Dxva2.INSTANCE.GetPhysicalMonitorsFromHMONITOR(hMonitor, monitorCount, monitors)) {
                    try {
                        for (var m : monitors) {
                            var name = m.getDescription();
                            var idx = index.getAndIncrement();
                            if (name == null || name.isBlank()) {
                                name = "Monitor " + idx;
                            }
                            result.add(new MonitorInfo("dxva2:" + idx, name));
                        }
                    } finally {
                        Dxva2.INSTANCE.DestroyPhysicalMonitors(monitorCount, monitors);
                    }
                }
            }
            return 1;
        }, null);
        return result;
    }

    public static boolean setBrightnessByIndex(int targetIndex, int brightness) {
        var index = new AtomicInteger(0);
        var handled = new boolean[] { false };
        User32.INSTANCE.EnumDisplayMonitors(null, null, (hMonitor, hdc, rect, lparam) -> {
            if (handled[0]) {
                return 0;
            }
            var count = new IntByReference();
            if (Dxva2.INSTANCE.GetNumberOfPhysicalMonitorsFromHMONITOR(hMonitor, count)) {
                var monitorCount = count.getValue();
                var monitors = (PHYSICAL_MONITOR[]) new PHYSICAL_MONITOR().toArray(monitorCount);
                if (Dxva2.INSTANCE.GetPhysicalMonitorsFromHMONITOR(hMonitor, monitorCount, monitors)) {
                    try {
                        for (var m : monitors) {
                            var idx = index.getAndIncrement();
                            if (idx == targetIndex) {
                                var min = new IntByReference();
                                var cur = new IntByReference();
                                var max = new IntByReference();
                                if (Dxva2.INSTANCE.GetMonitorBrightness(m.hPhysicalMonitor, min, cur, max)) {
                                    var scaled = scaleBrightness(brightness, min.getValue(), max.getValue());
                                    Dxva2.INSTANCE.SetMonitorBrightness(m.hPhysicalMonitor, scaled);
                                    handled[0] = true;
                                    return 0;
                                }
                            }
                        }
                    } finally {
                        Dxva2.INSTANCE.DestroyPhysicalMonitors(monitorCount, monitors);
                    }
                }
            }
            return 1;
        }, null);
        return handled[0];
    }

    private static int scaleBrightness(int value, int min, int max) {
        var clamped = Math.max(0, Math.min(100, value));
        if (max <= min) {
            return clamped;
        }
        return min + Math.round((max - min) * (clamped / 100.0f));
    }

    private interface Dxva2 extends StdCallLibrary {
        Dxva2 INSTANCE = Native.load("dxva2", Dxva2.class, W32APIOptions.UNICODE_OPTIONS);

        boolean GetNumberOfPhysicalMonitorsFromHMONITOR(HMONITOR hMonitor, IntByReference number);

        boolean GetPhysicalMonitorsFromHMONITOR(HMONITOR hMonitor, int count, PHYSICAL_MONITOR[] monitors);

        boolean DestroyPhysicalMonitors(int count, PHYSICAL_MONITOR[] monitors);

        boolean GetMonitorBrightness(HANDLE handle, IntByReference min, IntByReference current, IntByReference max);

        boolean SetMonitorBrightness(HANDLE handle, int brightness);
    }

    public static class PHYSICAL_MONITOR extends Structure {
        public HANDLE hPhysicalMonitor;
        public char[] szPhysicalMonitorDescription = new char[128];

        @Override
        protected List<String> getFieldOrder() {
            return List.of("hPhysicalMonitor", "szPhysicalMonitorDescription");
        }

        public String getDescription() {
            return Native.toString(szPhysicalMonitorDescription);
        }
    }

}
