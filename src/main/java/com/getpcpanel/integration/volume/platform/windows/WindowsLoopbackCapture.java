package com.getpcpanel.integration.volume.platform.windows;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.getpcpanel.integration.visualizer.Downmixer;
import com.getpcpanel.integration.volume.platform.LoopbackCapture;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * What an output plays, through WASAPI loopback ({@code IAudioClient} in shared mode with
 * {@code AUDCLNT_STREAMFLAGS_LOOPBACK}), or what an input hears (the same, on a capture endpoint without loopback), by
 * raw COM vtable calls like {@link CoreAudioMeterReader}. Polled: no event
 * handle and no callbacks, as JNA callbacks don't work in the native image. The mix (normally 48 kHz float stereo) is
 * downmixed to mono and decimated to about 22 kHz as it is read.
 *
 * <p>Loopback delivers no packets while nothing plays; that reads as silence. When the default device changes or the
 * device goes away, {@link #read} reports the capture broken so the caller starts it again.
 *
 * <p>Use from one thread only; it initialises COM (MTA) on it.
 */
@Log4j2
@WindowsBuild
@ApplicationScoped
class WindowsLoopbackCapture implements LoopbackCapture {
    // Created per instance (at run time): a static GUID would be folded into the native image heap.
    private final GUID clsidDeviceEnumerator = new GUID("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private final GUID iidDeviceEnumerator = new GUID("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private final GUID iidAudioClient = new GUID("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
    private final GUID iidCaptureClient = new GUID("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
    private static final int CLSCTX_ALL = 0x17;
    private static final int COINIT_MULTITHREADED = 0;
    private static final int RPC_E_CHANGED_MODE = 0x80010106;
    private static final int E_RENDER = 0;
    private static final int E_CAPTURE = 1;
    private static final int E_MULTIMEDIA = 1;
    private static final int AUDCLNT_SHAREMODE_SHARED = 0;
    private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int AUDCLNT_BUFFERFLAGS_SILENT = 0x2;
    private static final long BUFFER_100NS = 2_000_000L; // 200 ms
    private static final int WAVE_FORMAT_PCM = 1;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final long DEFAULT_CHECK_MS = 2_000;

    @Nullable private ComPtr enumerator;
    @Nullable private ComPtr client;
    @Nullable private ComPtr capture;
    @Nullable private String deviceId;
    @Nullable private Downmixer downmixer;
    private int channels;
    private int factor = 1;
    private boolean floats;
    private int rate = 24_000;
    private float[] interleaved = new float[0];
    private short[] pcm = new short[0];
    private long defaultCheckedAt;
    private boolean followsDefault;
    private int flow = E_RENDER;
    private boolean comFailed;
    // Reused for every call: a read happens 20 times a second.
    private final IntByReference packetSize = new IntByReference();
    private final PointerByReference data = new PointerByReference();
    private final IntByReference frames = new IntByReference();
    private final IntByReference flags = new IntByReference();

    @Override
    public boolean supported() {
        return !comFailed;
    }

    @Override
    @Nullable
    public String unavailableReason() {
        return comFailed ? "Windows audio could not be opened for the music visualizer; see the log." : null;
    }

    @Override
    public boolean start(@Nullable String id, boolean input) {
        stop();
        if (enumerator == null && !init()) {
            return false;
        }
        followsDefault = id == null;
        flow = input ? E_CAPTURE : E_RENDER;
        var device = id == null ? defaultDevice() : device(id);
        if (device == null) {
            return false; // no such device
        }
        try {
            deviceId = idOf(device);
            var ppv = new PointerByReference();
            // IMMDevice::Activate(IAudioClient)
            if (device.call(3, iidAudioClient, CLSCTX_ALL, null, ppv) < 0) {
                return false;
            }
            var audioClient = new ComPtr(ppv.getValue());
            if (!open(audioClient, input)) {
                audioClient.Release();
                return false;
            }
            client = audioClient;
            defaultCheckedAt = System.currentTimeMillis();
            return true;
        } finally {
            device.Release();
        }
    }

    private boolean open(ComPtr audioClient, boolean input) {
        var formatRef = new PointerByReference();
        if (audioClient.call(8, formatRef) < 0) { // IAudioClient::GetMixFormat
            return false;
        }
        var format = formatRef.getValue();
        try {
            var tag = format.getShort(0) & 0xFFFF;
            channels = format.getShort(2);
            var mixRate = format.getInt(4);
            var bits = format.getShort(14);
            if (tag == WAVE_FORMAT_EXTENSIBLE) {
                tag = format.getInt(24); // the SubFormat GUID's first field is the format tag
            }
            floats = tag == WAVE_FORMAT_IEEE_FLOAT && bits == 32;
            if (!floats && !(tag == WAVE_FORMAT_PCM && bits == 16) || channels < 1) {
                log.warn("The device's mix format ({} bit, tag {}, {} channels) can't be visualised", bits, tag, channels);
                return false;
            }
            // IAudioClient::Initialize(shared, loopback for an output, 200 ms, 0, mix format, no session)
            var hr = audioClient.call(3, AUDCLNT_SHAREMODE_SHARED, input ? 0 : AUDCLNT_STREAMFLAGS_LOOPBACK, BUFFER_100NS, 0L, format, null);
            if (hr < 0) {
                log.debug("Capture Initialize failed: {}", Integer.toHexString(hr));
                return false;
            }
            log.debug("Capturing {}: {} Hz, {} channels, {} bit, tag {}", deviceId, mixRate, channels, bits, tag);
            factor = Downmixer.factorFor(mixRate);
            rate = mixRate / factor;
            downmixer = new Downmixer(channels, factor);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(format);
        }
        var captureRef = new PointerByReference();
        if (audioClient.call(14, iidCaptureClient, captureRef) < 0) { // IAudioClient::GetService
            return false;
        }
        var captureClient = new ComPtr(captureRef.getValue());
        if (audioClient.call(10) < 0) { // IAudioClient::Start
            captureClient.Release();
            return false;
        }
        capture = captureClient;
        return true;
    }

    private boolean init() {
        var hr = Ole32.INSTANCE.CoInitializeEx(null, COINIT_MULTITHREADED);
        if (hr.intValue() < 0 && hr.intValue() != RPC_E_CHANGED_MODE) {
            log.warn("CoInitializeEx failed for the visualizer: {}", Integer.toHexString(hr.intValue()));
            comFailed = true;
            return false;
        }
        var ppv = new PointerByReference();
        hr = Ole32.INSTANCE.CoCreateInstance(clsidDeviceEnumerator, null, CLSCTX_ALL, iidDeviceEnumerator, ppv);
        if (hr.intValue() < 0) {
            log.warn("Creating the MMDeviceEnumerator for the visualizer failed: {}", Integer.toHexString(hr.intValue()));
            comFailed = true;
            return false;
        }
        enumerator = new ComPtr(ppv.getValue());
        return true;
    }

    @Nullable
    private ComPtr defaultDevice() {
        var ppv = new PointerByReference();
        // IMMDeviceEnumerator::GetDefaultAudioEndpoint
        return enumerator == null || enumerator.call(4, flow, E_MULTIMEDIA, ppv) < 0 ? null : new ComPtr(ppv.getValue());
    }

    @Nullable
    private ComPtr device(String id) {
        var ppv = new PointerByReference();
        // IMMDeviceEnumerator::GetDevice
        return enumerator == null || enumerator.call(5, new WString(id), ppv) < 0 ? null : new ComPtr(ppv.getValue());
    }

    @Nullable
    private static String idOf(ComPtr device) {
        var idPtr = new PointerByReference();
        if (device.call(5, idPtr) < 0) { // IMMDevice::GetId
            return null;
        }
        var id = idPtr.getValue().getWideString(0);
        Ole32.INSTANCE.CoTaskMemFree(idPtr.getValue());
        return id;
    }

    @Override
    @PreDestroy
    public void stop() {
        if (client != null) {
            client.call(11); // IAudioClient::Stop
        }
        if (capture != null) {
            capture.Release();
            capture = null;
        }
        if (client != null) {
            client.Release();
            client = null;
        }
        downmixer = null;
    }

    @Override
    public int sampleRate() {
        return rate;
    }

    @Override
    public int read(float[] into) {
        if (capture == null || downmixer == null) {
            return 0;
        }
        if (defaultChanged()) {
            return -1;
        }
        var written = 0;
        while (true) {
            if (capture.call(5, packetSize) < 0) { // IAudioCaptureClient::GetNextPacketSize
                return -1; // AUDCLNT_E_DEVICE_INVALIDATED and friends
            }
            var packet = packetSize.getValue();
            if (packet == 0) {
                return written;
            }
            if (written + packet / factor + 1 > into.length) {
                return written; // the rest waits for the next read
            }
            // IAudioCaptureClient::GetBuffer
            if (capture.call(3, data, frames, flags, null, null) < 0) {
                return -1;
            }
            var n = frames.getValue();
            var samples = n * channels;
            if (interleaved.length < samples) {
                interleaved = new float[samples];
            }
            if ((flags.getValue() & AUDCLNT_BUFFERFLAGS_SILENT) != 0) {
                Arrays.fill(interleaved, 0, samples, 0f);
            } else {
                copy(data.getValue(), samples);
            }
            written += downmixer.process(interleaved, n, into, written);
            capture.call(4, n); // IAudioCaptureClient::ReleaseBuffer
        }
    }

    private void copy(Pointer from, int samples) {
        if (floats) {
            from.read(0, interleaved, 0, samples);
            return;
        }
        if (pcm.length < samples) {
            pcm = new short[samples];
        }
        from.read(0, pcm, 0, samples);
        for (var i = 0; i < samples; i++) {
            interleaved[i] = pcm[i] / 32768f;
        }
    }

    private boolean defaultChanged() {
        if (!followsDefault) {
            return false;
        }
        var now = System.currentTimeMillis();
        if (now - defaultCheckedAt < DEFAULT_CHECK_MS) {
            return false;
        }
        defaultCheckedAt = now;
        var device = defaultDevice();
        if (device == null) {
            return true;
        }
        try {
            var id = idOf(device);
            return id != null && !id.equals(deviceId);
        } finally {
            device.Release();
        }
    }
}
