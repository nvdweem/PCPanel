package com.getpcpanel.integration.volume.platform.osx;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.getpcpanel.util.concurrent.AppThreads;
import com.sun.jna.Memory;
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
 * <p>The aggregate starts its IO with the process's first sound ({@code tapautostart}) but keeps it running after the
 * process goes quiet, calling the IO proc about a hundred times a second for nothing. So once the process has no
 * output running the aggregate is stopped and started again, which puts it back to waiting for the next sound. A
 * change of the process's running state ('pir?'; 'piro' itself sends no notification) asks for that check, and a
 * slow periodic one covers what that misses: a process that keeps its input running, or IO resumed by a wake.
 *
 * <p>Creating a tap requires the "System Audio Recording" TCC consent
 * ({@code NSAudioCaptureUsageDescription}); without it creation fails with an OSStatus error.
 */
@Log4j2
final class ProcessTap implements Tap {
    // CATapMuteBehavior.mutedWhenTapped
    private static final long MUTED_WHEN_TAPPED = 2;
    private static final int BYTES_PER_FLOAT = 4;
    // AudioBufferList: UInt32 mNumberBuffers, then AudioBuffer[] at offset 8 (pointer-aligned),
    // each 16 bytes: UInt32 mNumberChannels, UInt32 mDataByteSize, void *mData.
    private static final int ABL_BUFFERS_OFFSET = 8;
    private static final int ABL_BUFFER_SIZE = 16;
    private static final int PROP_IS_RUNNING = CoreAudioWrapper.fourcc("pir?");
    private static final int PROP_IS_RUNNING_OUTPUT = CoreAudioWrapper.fourcc("piro");
    static final Duration IDLE_CHECK_PERIOD = Duration.ofSeconds(10);
    private static final ScheduledExecutorService IDLE = Executors.newSingleThreadScheduledExecutor(AppThreads.factory("tap-idle", true));
    // Listeners CoreAudio failed to remove may still be called, so they stay reachable for good.
    private static final Set<Object> UNREMOVED_LISTENERS = ConcurrentHashMap.newKeySet();
    private static final Hal COREAUDIO = new Hal() {
        @Override
        public int runningOutput(int processObject) {
            try (var data = new Memory(Integer.BYTES)) {
                var size = new IntByReference(Integer.BYTES);
                var status = CoreAudioLib.INSTANCE.AudioObjectGetPropertyData(processObject, globalAddress(PROP_IS_RUNNING_OUTPUT), 0, null, size, data);
                return status != 0 ? -1 : data.getInt(0) == 0 ? 0 : 1;
            }
        }

        @Override
        public int start(int aggregateId, Pointer ioProcId) {
            return CoreAudioTapLib.INSTANCE.AudioDeviceStart(aggregateId, ioProcId);
        }

        @Override
        public int stop(int aggregateId, Pointer ioProcId) {
            return CoreAudioTapLib.INSTANCE.AudioDeviceStop(aggregateId, ioProcId);
        }
    };

    private final int tapId;
    private final int aggregateId;
    private final int processObject;
    private final Hal hal;
    private Pointer ioProcId;
    // Strong references: JNA only weakly tracks callbacks, but CoreAudio keeps the function pointers.
    @SuppressWarnings({ "FieldCanBeLocal", "unused" }) private CoreAudioTapLib.AudioDeviceIOProc renderProc;
    private @Nullable CoreAudioLib.AudioObjectPropertyListenerProc runningListener;
    private @Nullable ScheduledFuture<?> idleCheck;
    private final AtomicBoolean idleCheckQueued = new AtomicBoolean();
    private volatile boolean renderedSinceArmed;
    private volatile boolean failed;
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

    /** The CoreAudio calls of the idle check. */
    interface Hal {
        /** 1 while the process runs output IO, 0 while it runs none, -1 when it cannot be read (the process is gone). */
        int runningOutput(int processObject);

        int start(int aggregateId, Pointer ioProcId);

        int stop(int aggregateId, Pointer ioProcId);
    }

    ProcessTap(int tapId, int aggregateId, int processObject, Hal hal, float gain, boolean muted) {
        this.tapId = tapId;
        this.aggregateId = aggregateId;
        this.processObject = processObject;
        this.hal = hal;
        this.gain = Math.clamp(gain, 0, 1);
        this.muted = muted;
    }

    /**
     * Taps the given HAL process object (see {@link CoreAudioWrapper#translatePidToProcessObject}) and
     * starts re-rendering its audio to the device with the given UID at {@code initialGain} and
     * {@code initialMuted}, both in place before the first render.
     *
     * @throws TapCreationException when any CoreAudio call fails (missing TCC consent, dead process, …)
     */
    static ProcessTap create(int processObject, String outputDeviceUid, float initialGain, boolean initialMuted) {
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

            var result = new ProcessTap(tapId, aggregateId, processObject, COREAUDIO, initialGain, initialMuted);
            log.debug("Starting process tap {} aggregate {} process object {} output '{}' initial gain {} muted {}", tapId, aggregateId, processObject, outputDeviceUid, initialGain, initialMuted);
            result.startRendering();
            result.watchIdle();
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

    private void watchIdle() {
        CoreAudioLib.AudioObjectPropertyListenerProc listener = (objectId, numberAddresses, addresses, clientData) -> {
            queueIdleCheck();
            return 0;
        };
        var status = CoreAudioLib.INSTANCE.AudioObjectAddPropertyListener(processObject, globalAddress(PROP_IS_RUNNING), listener, null);
        if (status == 0) {
            runningListener = listener;
        } else {
            log.debug("Unable to follow process object {} running state ({}), checking it every {}", processObject, fourCharCode(status), IDLE_CHECK_PERIOD);
        }
        idleCheck = IDLE.scheduleWithFixedDelay(this::checkIdle, IDLE_CHECK_PERIOD.toMillis(), IDLE_CHECK_PERIOD.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Called on CoreAudio's notification thread, which must not stop or start devices: hands the check over. */
    private void queueIdleCheck() {
        if (!idleCheckQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            IDLE.execute(() -> {
                idleCheckQueued.set(false);
                checkIdle();
            });
        } catch (RejectedExecutionException e) {
            idleCheckQueued.set(false);
        }
    }

    /**
     * Puts the aggregate back to waiting for the process's next sound when it rendered since it last waited and the
     * process now runs no output. A process that cannot be read is left alone: it is gone, and the service closes its tap.
     */
    synchronized void checkIdle() {
        if (closed || failed || !renderedSinceArmed || hal.runningOutput(processObject) != 0) {
            return;
        }
        logOnError(hal.stop(aggregateId, ioProcId), "AudioDeviceStop");
        renderedSinceArmed = false;
        var status = hal.start(aggregateId, ioProcId);
        if (status == 0) {
            log.trace("Process tap {} idle, waiting for the next sound", tapId);
        } else {
            failed = true;
            log.warn("Unable to restart process tap {} after its process went quiet ({}), recreating it on the next volume change", tapId, fourCharCode(status));
        }
    }

    @Override
    public boolean alive() {
        return !failed;
    }

    private static CoreAudioLib.AudioObjectPropertyAddress globalAddress(int selector) {
        return new CoreAudioLib.AudioObjectPropertyAddress(selector, CoreAudioWrapper.SCOPE_GLOBAL, 0);
    }

    private static void throwOnError(int status, String call) {
        if (status != 0) {
            throw new TapCreationException(call, status);
        }
    }

    @Override
    public void setGain(float gain) {
        this.gain = Math.clamp(gain, 0, 1);
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    /**
     * Realtime render: copy the tap's stereo input to the output device at the current gain. The tap is
     * the trailing 2-channel input buffer (taps follow the sub-device's own streams in the aggregate's
     * input list); all other output stays silent.
     */
    int render(@Nullable Pointer inputData, @Nullable Pointer outputData) {
        renderedSinceArmed = true;
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
        log.debug("Closing process tap {} aggregate {} gain {} muted {}", tapId, aggregateId, gain, muted);
        if (idleCheck != null) {
            idleCheck.cancel(false);
        }
        if (runningListener != null) {
            var status = CoreAudioLib.INSTANCE.AudioObjectRemovePropertyListener(processObject, globalAddress(PROP_IS_RUNNING), runningListener, null);
            if (status != 0) {
                UNREMOVED_LISTENERS.add(runningListener);
                logOnError(status, "AudioObjectRemovePropertyListener");
            }
        }
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
