package com.getpcpanel.integration.volume.platform.windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.AudioLevelMeter;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Peak meters from Windows Core Audio ({@code IAudioMeterInformation}), through raw COM vtable calls like
 * {@code IShellItemImageFactory}. Kept apart from {@code SndCtrl.dll} on purpose: it runs on the caller's own MTA
 * thread and never touches the DLL's notification callbacks.
 *
 * <p>A device's meter reads what is sent to it, before its own Windows volume: Wave Link, for one, sets the volume of
 * its virtual devices from its faders, so a device's peak is scaled by its volume (and is silent while muted).
 *
 * <p>{@link #sample()} must always be called from the same thread; it initialises COM on it the first time. The
 * session meters are re-enumerated every {@link #SESSION_REFRESH_MS} (apps start and stop playing), the device
 * meters are kept until a read fails.
 */
@Log4j2
@WindowsBuild
@ApplicationScoped
class WindowsAudioLevelMeter implements AudioLevelMeter {
    // Created per instance (at run time): a static GUID would be folded into the native image heap.
    private final GUID clsidDeviceEnumerator = new GUID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private final GUID iidDeviceEnumerator = new GUID("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private final GUID iidMeterInformation = new GUID("C02216F6-8C67-4B5B-9D00-D008E73E0064");
    private final GUID iidSessionManager2 = new GUID("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
    private final GUID iidEndpointVolume = new GUID("5CDF2C82-841E-4546-9722-0CF74078229A");
    private static final int CLSCTX_ALL = 0x17;
    private static final int COINIT_MULTITHREADED = 0;
    private static final int E_RENDER = 0;
    private static final int E_MULTIMEDIA = 1;
    private static final int DEVICE_STATE_ACTIVE = 1;
    private static final long SESSION_REFRESH_MS = 2_000;

    @Nullable private Com enumerator;
    private final Map<String, DeviceMeter> deviceMeters = new HashMap<>();
    private final List<SessionMeter> sessionMeters = new ArrayList<>();
    private List<String> deviceIds = List.of();
    private long sessionsReadAt;
    private boolean failed;

    private record SessionMeter(int pid, Com meter) {
    }

    /** A device's peak meter, and its volume control when it has one. */
    private record DeviceMeter(Com meter, @Nullable Com volume) {
        void release() {
            meter.Release();
            if (volume != null) {
                volume.Release();
            }
        }
    }

    @Override
    public boolean supported() {
        return !failed; // after a failure the lights show their loud colour rather than reading silence
    }

    @Override
    public Levels sample() {
        if (failed) {
            return Levels.NONE;
        }
        try {
            if (enumerator == null && !init()) {
                failed = true;
                return Levels.NONE;
            }
            var now = System.currentTimeMillis();
            if (now - sessionsReadAt > SESSION_REFRESH_MS) {
                deviceIds = activeRenderDeviceIds();
                refreshSessions();
                sessionsReadAt = now;
            }
            var byPid = new HashMap<Integer, Float>();
            for (var s : sessionMeters) {
                var peak = peak(s.meter());
                if (peak >= 0) {
                    byPid.merge(s.pid(), peak, Math::max);
                }
            }
            var byDevice = new HashMap<String, Float>();
            for (var id : deviceIds) {
                var peak = devicePeak(id);
                if (peak >= 0) {
                    byDevice.put(id, peak);
                }
            }
            return new Snapshot(byPid, byDevice, Math.max(0, devicePeak("")));
        } catch (Throwable t) {
            log.warn("Audio level metering failed; audio-level lights show their loud colour from now on", t);
            failed = true;
            return Levels.NONE;
        }
    }

    private boolean init() {
        var hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_MULTITHREADED);
        if (hr.intValue() < 0 && hr.intValue() != 0x80010106) { // RPC_E_CHANGED_MODE: COM already up in another mode
            log.warn("CoInitializeEx failed: {}", Integer.toHexString(hr.intValue()));
            return false;
        }
        var ppv = new PointerByReference();
        hr = Ole32.INSTANCE.CoCreateInstance(clsidDeviceEnumerator, null, CLSCTX_ALL, iidDeviceEnumerator, ppv);
        if (hr.intValue() < 0) {
            log.warn("Creating the MMDeviceEnumerator failed: {}", Integer.toHexString(hr.intValue()));
            return false;
        }
        enumerator = new Com(ppv.getValue());
        return true;
    }

    /** Peak of the device with this endpoint id ("" = default output), or -1 when it cannot be read. */
    private float devicePeak(String id) {
        var meter = deviceMeters.get(id);
        if (meter == null) {
            meter = activateDeviceMeter(id);
            if (meter == null) {
                return -1;
            }
            deviceMeters.put(id, meter);
        }
        var peak = peak(meter.meter());
        if (peak < 0) {
            deviceMeters.remove(id).release();
            return peak;
        }
        return meter.volume() == null ? peak : audibleThrough(meter.volume(), peak);
    }

    /** {@code peak} after the device's own volume and mute; the peak as it is when they cannot be read. */
    private static float audibleThrough(Com volume, float peak) {
        var db = new FloatByReference();
        var muted = new IntByReference();
        // IAudioEndpointVolume::GetMasterVolumeLevel / ::GetMute
        if (volume.call(8, db) < 0 || volume.call(15, muted) < 0) {
            return peak;
        }
        return audible(peak, db.getValue(), muted.getValue() != 0);
    }

    /** A peak measured before a volume of {@code volumeDb}, as it comes out. */
    static float audible(float peak, float volumeDb, boolean muted) {
        return muted ? 0 : (float) (peak * Math.pow(10, volumeDb / 20));
    }

    @Nullable
    private DeviceMeter activateDeviceMeter(String id) {
        var device = device(id);
        if (device == null) {
            return null;
        }
        try {
            var ppv = new PointerByReference();
            // IMMDevice::Activate
            if (device.call(3, iidMeterInformation, CLSCTX_ALL, null, ppv) < 0) {
                return null;
            }
            var meter = new Com(ppv.getValue());
            var volume = new PointerByReference();
            return new DeviceMeter(meter, device.call(3, iidEndpointVolume, CLSCTX_ALL, null, volume) < 0 ? null : new Com(volume.getValue()));
        } finally {
            device.Release();
        }
    }

    @Nullable
    private Com device(String id) {
        var ppv = new PointerByReference();
        // IMMDeviceEnumerator::GetDefaultAudioEndpoint / ::GetDevice
        var hr = id.isEmpty() ? enumerator.call(4, E_RENDER, E_MULTIMEDIA, ppv) : enumerator.call(5, new WString(id), ppv);
        return hr < 0 ? null : new Com(ppv.getValue());
    }

    private List<String> activeRenderDeviceIds() {
        var ids = new ArrayList<String>();
        var collection = new PointerByReference();
        // IMMDeviceEnumerator::EnumAudioEndpoints
        if (enumerator.call(3, E_RENDER, DEVICE_STATE_ACTIVE, collection) < 0) {
            return ids;
        }
        var devices = new Com(collection.getValue());
        try {
            var count = new IntByReference();
            devices.call(3, count); // IMMDeviceCollection::GetCount
            for (var i = 0; i < count.getValue(); i++) {
                var item = new PointerByReference();
                if (devices.call(4, i, item) < 0) { // IMMDeviceCollection::Item
                    continue;
                }
                var device = new Com(item.getValue());
                try {
                    var idPtr = new PointerByReference();
                    if (device.call(5, idPtr) >= 0) { // IMMDevice::GetId
                        ids.add(idPtr.getValue().getWideString(0));
                        Ole32.INSTANCE.CoTaskMemFree(idPtr.getValue());
                    }
                } finally {
                    device.Release();
                }
            }
        } finally {
            devices.Release();
        }
        return ids;
    }

    private void refreshSessions() {
        sessionMeters.forEach(s -> s.meter().Release());
        sessionMeters.clear();
        for (var id : deviceIds) {
            var device = device(id);
            if (device == null) {
                continue;
            }
            try {
                var managerPtr = new PointerByReference();
                if (device.call(3, iidSessionManager2, CLSCTX_ALL, null, managerPtr) < 0) { // IMMDevice::Activate
                    continue;
                }
                var manager = new Com(managerPtr.getValue());
                try {
                    readSessions(manager);
                } finally {
                    manager.Release();
                }
            } finally {
                device.Release();
            }
        }
    }

    private void readSessions(Com manager) {
        var enumPtr = new PointerByReference();
        if (manager.call(5, enumPtr) < 0) { // IAudioSessionManager2::GetSessionEnumerator
            return;
        }
        var sessions = new Com(enumPtr.getValue());
        try {
            var count = new IntByReference();
            sessions.call(3, count); // IAudioSessionEnumerator::GetCount
            for (var i = 0; i < count.getValue(); i++) {
                var controlPtr = new PointerByReference();
                if (sessions.call(4, i, controlPtr) < 0) { // IAudioSessionEnumerator::GetSession
                    continue;
                }
                var control = new Com(controlPtr.getValue());
                try {
                    addSession(control);
                } finally {
                    control.Release();
                }
            }
        } finally {
            sessions.Release();
        }
    }

    private void addSession(Com control) {
        var control2Ptr = new PointerByReference();
        if (control.QueryInterface(new REFIID(new IID("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D")), control2Ptr).intValue() < 0) {
            return;
        }
        var control2 = new Com(control2Ptr.getValue());
        try {
            var pid = new IntByReference();
            if (control2.call(14, pid) < 0) { // IAudioSessionControl2::GetProcessId
                return;
            }
            var meterPtr = new PointerByReference();
            if (control2.QueryInterface(new REFIID(new IID("C02216F6-8C67-4B5B-9D00-D008E73E0064")), meterPtr).intValue() < 0) {
                return;
            }
            sessionMeters.add(new SessionMeter(pid.getValue(), new Com(meterPtr.getValue())));
        } finally {
            control2.Release();
        }
    }

    /** IAudioMeterInformation::GetPeakValue, or -1 when it fails. */
    private static float peak(Com meter) {
        var value = new FloatByReference();
        return meter.call(3, value) < 0 ? -1 : value.getValue();
    }

    /** A COM interface pointer whose methods are called by vtable index. */
    private static final class Com extends Unknown {
        Com(Pointer p) {
            super(p);
        }

        int call(int vtableIndex, Object... args) {
            var full = new Object[args.length + 1];
            full[0] = getPointer();
            System.arraycopy(args, 0, full, 1, args.length);
            return ((HRESULT) _invokeNativeObject(vtableIndex, full, HRESULT.class)).intValue();
        }
    }
}
