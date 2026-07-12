package com.getpcpanel.integration.volume.platform.osx;

import java.util.Arrays;
import java.util.UUID;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import lombok.extern.log4j.Log4j2;

/**
 * One live per-process volume control (macOS 14.4+): a CoreAudio process tap on the target process with
 * {@code mutedWhenTapped} (the OS silences the process's direct output while the tap runs), wrapped in a
 * private aggregate device whose IO proc re-renders the tapped audio to the real output device at
 * {@link #setGain}. Gain 0 is true silence; {@link #close()} removes the tap and the process's own output
 * resumes untouched.
 *
 * <p>Creating a tap requires the "System Audio Recording" TCC consent
 * ({@code NSAudioCaptureUsageDescription}); without it creation fails with an OSStatus error.
 */
@Log4j2
final class ProcessTap implements AutoCloseable {
    // CATapMuteBehavior.mutedWhenTapped
    private static final long MUTED_WHEN_TAPPED = 2;
    private static final int BYTES_PER_FLOAT = 4;
    // AudioBufferList: UInt32 mNumberBuffers, then AudioBuffer[] at offset 8 (pointer-aligned),
    // each 16 bytes: UInt32 mNumberChannels, UInt32 mDataByteSize, void *mData.
    private static final int ABL_BUFFERS_OFFSET = 8;
    private static final int ABL_BUFFER_SIZE = 16;

    private final int tapId;
    private final int aggregateId;
    private Pointer ioProcId;
    // Strong reference: JNA only weakly tracks callbacks, but CoreAudio keeps the function pointer.
    @SuppressWarnings({ "FieldCanBeLocal", "unused" }) private CoreAudioTapLib.AudioDeviceIOProc renderProc;
    private volatile float gain;
    private volatile boolean muted;
    private boolean closed;
    // Scratch buffers reused across render callbacks (CoreAudio serializes IO per device).
    private float[] tapSamples = new float[0];
    private float[] outSamples = new float[0];

    /** Marks a failed CoreAudio call; {@code status} is the OSStatus (often a four-char code). */
    static class TapCreationException extends RuntimeException {
        TapCreationException(String call, int status) {
            super(call + " failed with status " + status + " ('" + fourCharCode(status) + "')");
        }
    }

    private ProcessTap(int tapId, int aggregateId, float gain) {
        this.tapId = tapId;
        this.aggregateId = aggregateId;
        this.gain = gain;
    }

    /**
     * Taps the given HAL process object (see {@link CoreAudioWrapper#translatePidToProcessObject}) and
     * starts re-rendering its audio to the device with the given UID at {@code initialGain}.
     *
     * @throws TapCreationException when any CoreAudio call fails (missing TCC consent, dead process, …)
     */
    static ProcessTap create(int processObject, String outputDeviceUid, float initialGain) {
        var lib = CoreAudioTapLib.INSTANCE;
        var tapId = 0;
        var aggregateId = 0;
        var pool = ObjcFoundation.autoreleasePoolPush();
        try {
            var description = ObjcFoundation.msg(ObjcFoundation.cls("CATapDescription"), "alloc");
            description = ObjcFoundation.msg(description, "initStereoMixdownOfProcesses:", ObjcFoundation.nsArray(ObjcFoundation.nsNumber(processObject)));
            try {
                ObjcFoundation.msg(description, "setName:", ObjcFoundation.nsString("PCPanel process tap"));
                ObjcFoundation.msg(description, "setPrivate:", 1);
                ObjcFoundation.msg(description, "setMuteBehavior:", MUTED_WHEN_TAPPED);

                var tapIdRef = new IntByReference();
                throwOnError(lib.AudioHardwareCreateProcessTap(description, tapIdRef), "AudioHardwareCreateProcessTap");
                tapId = tapIdRef.getValue();

                var tapUuid = ObjcFoundation.javaString(ObjcFoundation.msg(ObjcFoundation.msg(description, "UUID"), "UUIDString"));
                var aggregateIdRef = new IntByReference();
                throwOnError(lib.AudioHardwareCreateAggregateDevice(aggregateDescription(outputDeviceUid, tapUuid), aggregateIdRef), "AudioHardwareCreateAggregateDevice");
                aggregateId = aggregateIdRef.getValue();
            } finally {
                ObjcFoundation.release(description);
            }

            var result = new ProcessTap(tapId, aggregateId, initialGain);
            result.startRendering();
            return result;
        } catch (RuntimeException e) {
            if (aggregateId != 0) {
                lib.AudioHardwareDestroyAggregateDevice(aggregateId);
            }
            if (tapId != 0) {
                lib.AudioHardwareDestroyProcessTap(tapId);
            }
            throw e;
        } finally {
            ObjcFoundation.autoreleasePoolPop(pool);
        }
    }

    /**
     * The aggregate wrapping the real output device plus the tap: private (invisible in sound settings),
     * drift-compensated, auto-starting the tap with the device's IO.
     */
    private static Pointer aggregateDescription(String outputDeviceUid, String tapUuid) {
        var subDevice = ObjcFoundation.nsMutableDictionary();
        ObjcFoundation.put(subDevice, "uid", ObjcFoundation.nsString(outputDeviceUid));
        var tap = ObjcFoundation.nsMutableDictionary();
        ObjcFoundation.put(tap, "uid", ObjcFoundation.nsString(tapUuid));
        ObjcFoundation.put(tap, "drift", ObjcFoundation.nsBool(true));

        var description = ObjcFoundation.nsMutableDictionary();
        ObjcFoundation.put(description, "name", ObjcFoundation.nsString("PCPanel volume"));
        ObjcFoundation.put(description, "uid", ObjcFoundation.nsString("com.getpcpanel.tap." + UUID.randomUUID()));
        ObjcFoundation.put(description, "master", ObjcFoundation.nsString(outputDeviceUid));
        ObjcFoundation.put(description, "private", ObjcFoundation.nsBool(true));
        ObjcFoundation.put(description, "stacked", ObjcFoundation.nsBool(false));
        ObjcFoundation.put(description, "subdevices", ObjcFoundation.nsArray(subDevice));
        ObjcFoundation.put(description, "taps", ObjcFoundation.nsArray(tap));
        ObjcFoundation.put(description, "tapautostart", ObjcFoundation.nsBool(true));
        return description;
    }

    private void startRendering() {
        var lib = CoreAudioTapLib.INSTANCE;
        var procIdRef = new PointerByReference();
        CoreAudioTapLib.AudioDeviceIOProc proc = (device, now, inputData, inputTime, outputData, outputTime, clientData) -> render(inputData, outputData);
        throwOnError(lib.AudioDeviceCreateIOProcID(aggregateId, proc, null, procIdRef), "AudioDeviceCreateIOProcID");
        ioProcId = procIdRef.getValue();
        renderProc = proc;
        var startStatus = lib.AudioDeviceStart(aggregateId, ioProcId);
        if (startStatus != 0) {
            lib.AudioDeviceDestroyIOProcID(aggregateId, ioProcId);
            throw new TapCreationException("AudioDeviceStart", startStatus);
        }
    }

    private static void throwOnError(int status, String call) {
        if (status != 0) {
            throw new TapCreationException(call, status);
        }
    }

    void setGain(float gain) {
        this.gain = Math.clamp(gain, 0, 1);
    }

    float gain() {
        return gain;
    }

    void setMuted(boolean muted) {
        this.muted = muted;
    }

    boolean muted() {
        return muted;
    }

    /**
     * Realtime render: copy the tap's stereo input to the output device at the current gain. The tap is
     * the trailing 2-channel input buffer (taps follow the sub-device's own streams in the aggregate's
     * input list); all other output stays silent.
     */
    private int render(Pointer inputData, Pointer outputData) {
        if (inputData == null || outputData == null) {
            return 0;
        }
        var outCount = outputData.getInt(0);
        for (var i = 0; i < outCount; i++) {
            var offset = ABL_BUFFERS_OFFSET + (long) i * ABL_BUFFER_SIZE;
            var data = outputData.getPointer(offset + 8);
            if (data != null) {
                data.setMemory(0, outputData.getInt(offset + 4), (byte) 0);
            }
        }

        var tapOffset = -1L;
        var inCount = inputData.getInt(0);
        for (var i = inCount - 1; i >= 0; i--) {
            var offset = ABL_BUFFERS_OFFSET + (long) i * ABL_BUFFER_SIZE;
            if (inputData.getInt(offset) == 2) {
                tapOffset = offset;
                break;
            }
        }
        if (tapOffset < 0 || outCount == 0) {
            return 0;
        }
        var tapData = inputData.getPointer(tapOffset + 8);
        var outOffset = firstStereoCapableBuffer(outputData, outCount);
        var outData = outOffset < 0 ? null : outputData.getPointer(outOffset + 8);
        if (tapData == null || outData == null) {
            return 0;
        }

        var effectiveGain = muted ? 0 : gain;
        var tapFloats = inputData.getInt(tapOffset + 4) / BYTES_PER_FLOAT;
        var outChannels = outputData.getInt(outOffset);
        var outFloats = outputData.getInt(outOffset + 4) / BYTES_PER_FLOAT;
        if (tapSamples.length < tapFloats || outSamples.length < outFloats) {
            tapSamples = new float[Math.max(tapSamples.length, tapFloats)];
            outSamples = new float[Math.max(outSamples.length, outFloats)];
        }
        tapData.read(0, tapSamples, 0, tapFloats);
        Arrays.fill(outSamples, 0, outFloats, 0);
        var frames = Math.min(tapFloats / 2, outFloats / outChannels);
        for (var frame = 0; frame < frames; frame++) {
            outSamples[frame * outChannels] = tapSamples[frame * 2] * effectiveGain;
            outSamples[frame * outChannels + 1] = tapSamples[frame * 2 + 1] * effectiveGain;
        }
        outData.write(0, outSamples, 0, outFloats);
        return 0;
    }

    private static long firstStereoCapableBuffer(Pointer bufferList, int count) {
        for (var i = 0; i < count; i++) {
            var offset = ABL_BUFFERS_OFFSET + (long) i * ABL_BUFFER_SIZE;
            if (bufferList.getInt(offset) >= 2) {
                return offset;
            }
        }
        return -1;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        var lib = CoreAudioTapLib.INSTANCE;
        logOnError(lib.AudioDeviceStop(aggregateId, ioProcId), "AudioDeviceStop");
        logOnError(lib.AudioDeviceDestroyIOProcID(aggregateId, ioProcId), "AudioDeviceDestroyIOProcID");
        logOnError(lib.AudioHardwareDestroyAggregateDevice(aggregateId), "AudioHardwareDestroyAggregateDevice");
        logOnError(lib.AudioHardwareDestroyProcessTap(tapId), "AudioHardwareDestroyProcessTap");
    }

    private static void logOnError(int status, String call) {
        if (status != 0) {
            log.debug("{} failed with status {} ('{}')", call, status, fourCharCode(status));
        }
    }

    private static String fourCharCode(int status) {
        var chars = new char[] { (char) (status >>> 24 & 0xFF), (char) (status >>> 16 & 0xFF), (char) (status >>> 8 & 0xFF), (char) (status & 0xFF) };
        for (var c : chars) {
            if (c < 0x20 || c > 0x7E) {
                return String.valueOf(status);
            }
        }
        return String.valueOf(chars);
    }
}
