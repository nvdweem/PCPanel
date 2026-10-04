package com.getpcpanel.integration.volume.platform.windows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.getpcpanel.integration.volume.platform.AudioLevelMeter.Levels;
import com.getpcpanel.integration.volume.platform.AudioLevelMeter.Snapshot;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import lombok.extern.log4j.Log4j2;

/**
 * Peak meters from Windows Core Audio ({@code IAudioMeterInformation}), through raw COM vtable calls like
 * {@code IShellItemImageFactory}. Kept apart from {@code SndCtrl.dll} on purpose: it runs on its caller's own MTA
 * thread and never touches the DLL's notification callbacks.
 *
 * <p>A device's meter reads what is sent to it, before its own Windows volume: Wave Link, for one, sets the volume of
 * its virtual devices from its faders, so a device's peak is scaled by its volume (and is silent while muted).
 *
 * <p>One instance per thread: {@link #sample()} must always be called from the thread that made the first call; it
 * initialises COM on it. The session meters are re-enumerated every {@link #SESSION_REFRESH_MS} (apps start and stop
 * playing), the device meters are kept until a read fails.
 */
@Log4j2
final class CoreAudioMeterReader {
    // Created per instance (at run time): a static GUID would be folded into the native image heap.
    private final GUID clsidDeviceEnumerator = new GUID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private final GUID iidDeviceEnumerator = new GUID("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private final GUID iidMeterInformation = new GUID("C02216F6-8C67-4B5B-9D00-D008E73E0064");
    private final GUID iidSessionManager2 = new GUID("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
    private final GUID iidEndpointVolume = new GUID("5CDF2C82-841E-4546-9722-0CF74078229A");
    private static final int CLSCTX_ALL = 0x17;
    private static final int COINIT_MULTITHREADED = 0;
    private static final int E_RENDER = 0;
    private static final int E_CAPTURE = 1;
    private static final int E_MULTIMEDIA = 1;
    private static final int DEVICE_STATE_ACTIVE = 1;
    private static final long SESSION_REFRESH_MS = 2_000;
    private final long sessionRefreshMs;

    @Nullable private ComPtr enumerator;
    private final Map<String, DeviceMeter> deviceMeters = new HashMap<>();
    private final List<SessionMeter> sessionMeters = new ArrayList<>();
    /** The last {@link #sample()}'s session peaks by process and device ({@code pid|deviceId}). */
    private final Map<String, Float> lastSessionPeaks = new HashMap<>();
    private List<String> deviceIds = List.of();
    private long sessionsReadAt;
    private boolean failed;

    private record SessionMeter(int pid, String deviceId, ComPtr meter) {
    }

    /** A device's peak meter, and its volume control when it has one. */
    private record DeviceMeter(ComPtr meter, @Nullable ComPtr volume) {
        void release() {
            meter.Release();
            if (volume != null) {
                volume.Release();
            }
        }
    }

    boolean supported() {
        return !failed; // after a failure the lights show their loud colour rather than reading silence
    }

    CoreAudioMeterReader() {
        this(SESSION_REFRESH_MS);
    }

    /** @param sessionRefreshMs how often the session list is read again even when nothing says it changed */
    CoreAudioMeterReader(long sessionRefreshMs) {
        this.sessionRefreshMs = sessionRefreshMs;
    }

    /** Reads the session list again on the next sample (an app started or stopped playing). */
    void invalidateSessions() {
        sessionsReadAt = Long.MIN_VALUE / 2;
    }

    /** Every session's and device's peak now; {@link Levels#NONE} after a failure. */
    Levels sample() {
        return sample(true);
    }

    /**
     * The peaks of the sessions {@code wanted} names ({@code pid|deviceId}), for {@link #sessionPeak}; no other meter is
     * read. Each read is a call into the Windows audio service, and expired sessions linger in the list.
     */
    void sampleSessions(Set<String> wanted) {
        sample(false, s -> wanted.contains(s.pid() + "|" + s.deviceId()));
    }

    /**
     * The peaks of every session of these processes, on every output, for {@link #sessionPeaks}: an app can have a
     * session on each output and play on only one of them.
     */
    void sampleProcesses(Set<Integer> pids) {
        sample(false, s -> pids.contains(s.pid()));
    }

    /** The last sample's session peaks by process and output ({@code pid|deviceId}). */
    Map<String, Float> sessionPeaks() {
        return Collections.unmodifiableMap(lastSessionPeaks);
    }

    private Levels sample(boolean devices) {
        return sample(devices, null);
    }

    private Levels sample(boolean devices, @Nullable Predicate<SessionMeter> wanted) {
        if (failed) {
            return Levels.NONE;
        }
        try {
            if (enumerator == null && !init()) {
                failed = true;
                return Levels.NONE;
            }
            var now = System.currentTimeMillis();
            if (now - sessionsReadAt > sessionRefreshMs) {
                deviceIds = activeRenderDeviceIds();
                refreshSessions();
                sessionsReadAt = now;
            }
            var byPid = new HashMap<Integer, Float>();
            lastSessionPeaks.clear();
            for (var s : sessionMeters) {
                if (wanted != null && !wanted.test(s)) {
                    continue;
                }
                var peak = peak(s.meter());
                if (peak >= 0) {
                    byPid.merge(s.pid(), peak, Math::max);
                    lastSessionPeaks.merge(s.pid() + "|" + s.deviceId(), peak, Math::max);
                }
            }
            if (!devices) {
                return Levels.NONE;
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
        enumerator = new ComPtr(ppv.getValue());
        return true;
    }

    /**
     * The peak of an output or input now ({@code id} null: the default one), after its own volume and mute; -1 when it
     * cannot be read. Reads that endpoint's meter alone, no session.
     */
    float endpointPeak(@Nullable String id, boolean input) {
        if (failed) {
            return -1;
        }
        try {
            if (enumerator == null && !init()) {
                failed = true;
                return -1;
            }
            var resolved = id != null ? id : defaultId(input ? E_CAPTURE : E_RENDER);
            return resolved == null || resolved.isEmpty() ? -1 : devicePeak(resolved);
        } catch (Throwable t) {
            log.warn("Audio level metering failed; audio-level lights show their loud colour from now on", t);
            failed = true;
            return -1;
        }
    }

    /** The current default device's id for this data flow, so a meter follows the default when it changes. */
    @Nullable
    private String defaultId(int flow) {
        var ppv = new PointerByReference();
        // IMMDeviceEnumerator::GetDefaultAudioEndpoint
        if (enumerator.call(4, flow, E_MULTIMEDIA, ppv) < 0) {
            return null;
        }
        var device = new ComPtr(ppv.getValue());
        try {
            var idPtr = new PointerByReference();
            if (device.call(5, idPtr) < 0) { // IMMDevice::GetId
                return null;
            }
            var id = idPtr.getValue().getWideString(0);
            Ole32.INSTANCE.CoTaskMemFree(idPtr.getValue());
            return id;
        } finally {
            device.Release();
        }
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
    private static float audibleThrough(ComPtr volume, float peak) {
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
            var meter = new ComPtr(ppv.getValue());
            var volume = new PointerByReference();
            return new DeviceMeter(meter, device.call(3, iidEndpointVolume, CLSCTX_ALL, null, volume) < 0 ? null : new ComPtr(volume.getValue()));
        } finally {
            device.Release();
        }
    }

    @Nullable
    private ComPtr device(String id) {
        var ppv = new PointerByReference();
        // IMMDeviceEnumerator::GetDefaultAudioEndpoint / ::GetDevice
        var hr = id.isEmpty() ? enumerator.call(4, E_RENDER, E_MULTIMEDIA, ppv) : enumerator.call(5, new WString(id), ppv);
        return hr < 0 ? null : new ComPtr(ppv.getValue());
    }

    private List<String> activeRenderDeviceIds() {
        var ids = new ArrayList<String>();
        var collection = new PointerByReference();
        // IMMDeviceEnumerator::EnumAudioEndpoints
        if (enumerator.call(3, E_RENDER, DEVICE_STATE_ACTIVE, collection) < 0) {
            return ids;
        }
        var devices = new ComPtr(collection.getValue());
        try {
            var count = new IntByReference();
            devices.call(3, count); // IMMDeviceCollection::GetCount
            for (var i = 0; i < count.getValue(); i++) {
                var item = new PointerByReference();
                if (devices.call(4, i, item) < 0) { // IMMDeviceCollection::Item
                    continue;
                }
                var device = new ComPtr(item.getValue());
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
                var manager = new ComPtr(managerPtr.getValue());
                try {
                    readSessions(manager, id);
                } finally {
                    manager.Release();
                }
            } finally {
                device.Release();
            }
        }
    }

    private void readSessions(ComPtr manager, String deviceId) {
        var enumPtr = new PointerByReference();
        if (manager.call(5, enumPtr) < 0) { // IAudioSessionManager2::GetSessionEnumerator
            return;
        }
        var sessions = new ComPtr(enumPtr.getValue());
        try {
            var count = new IntByReference();
            sessions.call(3, count); // IAudioSessionEnumerator::GetCount
            for (var i = 0; i < count.getValue(); i++) {
                var controlPtr = new PointerByReference();
                if (sessions.call(4, i, controlPtr) < 0) { // IAudioSessionEnumerator::GetSession
                    continue;
                }
                var control = new ComPtr(controlPtr.getValue());
                try {
                    addSession(control, deviceId);
                } finally {
                    control.Release();
                }
            }
        } finally {
            sessions.Release();
        }
    }

    private void addSession(ComPtr control, String deviceId) {
        var control2Ptr = new PointerByReference();
        if (control.QueryInterface(new REFIID(new IID("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D")), control2Ptr).intValue() < 0) {
            return;
        }
        var control2 = new ComPtr(control2Ptr.getValue());
        try {
            var pid = new IntByReference();
            if (control2.call(14, pid) < 0) { // IAudioSessionControl2::GetProcessId
                return;
            }
            var meterPtr = new PointerByReference();
            if (control2.QueryInterface(new REFIID(new IID("C02216F6-8C67-4B5B-9D00-D008E73E0064")), meterPtr).intValue() < 0) {
                return;
            }
            sessionMeters.add(new SessionMeter(pid.getValue(), deviceId, new ComPtr(meterPtr.getValue())));
        } finally {
            control2.Release();
        }
    }

    /** A session's peak in the last {@link #sample()}, for one process on one output; 0 when it wasn't seen. */
    float sessionPeak(int pid, String deviceId) {
        return lastSessionPeaks.getOrDefault(pid + "|" + deviceId, 0f);
    }

    /** IAudioMeterInformation::GetPeakValue, or -1 when it fails. */
    private static float peak(ComPtr meter) {
        var value = new FloatByReference();
        return meter.call(3, value) < 0 ? -1 : value.getValue();
    }
}
