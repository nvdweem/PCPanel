package com.getpcpanel.integration.volume.platform.osx;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.getpcpanel.platform.MacBuild;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.mac.CoreFoundation;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Friendly wrapper around the CoreAudio HAL. All volumes are scalar 0..1, matching {@link com.getpcpanel.integration.volume.platform.ISndCtrl}.
 */
@Log4j2
@ApplicationScoped
@MacBuild
public class CoreAudioWrapper {
    // Accessed lazily (not via a static field) so this bean class stays build-time-initializable:
    // CoreAudioLib.INSTANCE runs Native.load in its own initializer, which must happen at run time.
    private static CoreAudioLib ca() {
        return CoreAudioLib.INSTANCE;
    }

    private static final int SYSTEM_OBJECT = CoreAudioLib.kAudioObjectSystemObject;
    private static final int ELEMENT_MAIN = 0;
    private static final int ELEMENT_WILDCARD = 0xFFFFFFFF;
    static final int SCOPE_GLOBAL = fourcc("glob");
    static final int SCOPE_OUTPUT = fourcc("outp");
    static final int SCOPE_INPUT = fourcc("inpt");
    static final int SCOPE_WILDCARD = fourcc("****");
    static final int PROP_DEVICES = fourcc("dev#");
    static final int PROP_DEFAULT_INPUT_DEVICE = fourcc("dIn ");
    static final int PROP_DEFAULT_OUTPUT_DEVICE = fourcc("dOut");
    static final int PROP_NAME = fourcc("lnam");
    static final int PROP_DEVICE_UID = fourcc("uid ");
    static final int PROP_STREAMS = fourcc("stm#");
    static final int PROP_VOLUME_SCALAR = fourcc("volm");
    static final int PROP_MUTE = fourcc("mute");
    private static final int PROP_RUN_LOOP = fourcc("rnlp");
    private static final int PROP_PREFERRED_STEREO_CHANNELS = fourcc("dch2");
    private static final int[] FALLBACK_CHANNELS = { 1, 2 };

    private final Set<String> warnedMissingControls = ConcurrentHashMap.newKeySet();

    public static final String INPUT_SUFFIX = ":input";

    /**
     * One entry per flow: a CoreAudio device with both input and output streams produces two entries
     * (the input entry id is suffixed with {@link #INPUT_SUFFIX}), mirroring the Windows endpoint model.
     */
    public record CoreAudioDevice(int id, String uid, String name, boolean output, float volume, boolean muted, boolean isDefault) {
    }

    public record ListenerHandle(int objectId, CoreAudioLib.AudioObjectPropertyAddress address, CoreAudioLib.AudioObjectPropertyListenerProc proc) {
    }

    @PostConstruct
    public void init() {
        // Move HAL notifications off the main run loop to CoreAudio's own thread
        try (var data = new Memory(Native.POINTER_SIZE)) {
            data.setPointer(0, null);
            var status = ca().AudioObjectSetPropertyData(SYSTEM_OBJECT, address(PROP_RUN_LOOP, SCOPE_GLOBAL, ELEMENT_MAIN), 0, null, (int) data.size(), data);
            if (status != 0) {
                log.debug("Unable to set CoreAudio run loop ({})", status);
            }
        }
    }

    public List<CoreAudioDevice> getDevices() {
        var defaultOutput = getDefaultDevice(true);
        var defaultInput = getDefaultDevice(false);
        var result = new ArrayList<CoreAudioDevice>();
        for (var deviceId : getDeviceIds()) {
            result.addAll(toDevices(deviceId, defaultOutput, defaultInput));
        }
        return result;
    }

    private List<CoreAudioDevice> toDevices(int deviceId, int defaultOutput, int defaultInput) {
        var uid = getString(deviceId, PROP_DEVICE_UID);
        if (uid == null) {
            return List.of();
        }
        var hasOutput = streamCount(deviceId, SCOPE_OUTPUT) > 0;
        var hasInput = streamCount(deviceId, SCOPE_INPUT) > 0;
        var name = getString(deviceId, PROP_NAME);
        if (name == null) {
            name = uid;
        }
        var result = new ArrayList<CoreAudioDevice>(2);
        if (hasOutput) {
            result.add(new CoreAudioDevice(deviceId, uid, name,
                    true, volumeOrZero(deviceId, SCOPE_OUTPUT), isMuted(deviceId, SCOPE_OUTPUT), deviceId == defaultOutput));
        }
        if (hasInput) {
            result.add(new CoreAudioDevice(deviceId, uid + INPUT_SUFFIX, hasOutput ? name + " (Input)" : name,
                    false, volumeOrZero(deviceId, SCOPE_INPUT), isMuted(deviceId, SCOPE_INPUT), deviceId == defaultInput));
        }
        return result;
    }

    private float volumeOrZero(int deviceId, int scope) {
        var volume = getVolume(deviceId, scope);
        return volume == null ? 0 : volume;
    }

    private boolean isMuted(int deviceId, int scope) {
        return Boolean.TRUE.equals(getMute(deviceId, scope));
    }

    public int[] getDeviceIds() {
        var address = address(PROP_DEVICES, SCOPE_GLOBAL, ELEMENT_MAIN);
        var size = new IntByReference();
        var status = ca().AudioObjectGetPropertyDataSize(SYSTEM_OBJECT, address, 0, null, size);
        if (status != 0 || size.getValue() == 0) {
            return new int[0];
        }
        try (var data = new Memory(size.getValue())) {
            status = ca().AudioObjectGetPropertyData(SYSTEM_OBJECT, address, 0, null, size, data);
            if (status != 0) {
                log.warn("Unable to list audio devices ({})", status);
                return new int[0];
            }
            return data.getIntArray(0, size.getValue() / 4);
        }
    }

    public int getDefaultDevice(boolean output) {
        var address = address(output ? PROP_DEFAULT_OUTPUT_DEVICE : PROP_DEFAULT_INPUT_DEVICE, SCOPE_GLOBAL, ELEMENT_MAIN);
        try (var data = new Memory(4)) {
            var size = new IntByReference(4);
            var status = ca().AudioObjectGetPropertyData(SYSTEM_OBJECT, address, 0, null, size, data);
            if (status != 0) {
                log.warn("Unable to determine default {} device ({})", output ? "output" : "input", status);
                return 0;
            }
            return data.getInt(0);
        }
    }

    public void setDefaultDevice(int deviceId, boolean output) {
        var address = address(output ? PROP_DEFAULT_OUTPUT_DEVICE : PROP_DEFAULT_INPUT_DEVICE, SCOPE_GLOBAL, ELEMENT_MAIN);
        try (var data = new Memory(4)) {
            data.setInt(0, deviceId);
            var status = ca().AudioObjectSetPropertyData(SYSTEM_OBJECT, address, 0, null, 4, data);
            if (status != 0) {
                log.warn("Unable to set default {} device to {} ({})", output ? "output" : "input", deviceId, status);
            }
        }
    }

    @Nullable
    public Float getVolume(int deviceId, int scope) {
        var master = getFloat(deviceId, PROP_VOLUME_SCALAR, scope, ELEMENT_MAIN);
        if (master != null) {
            return master;
        }
        var sum = 0f;
        var count = 0;
        for (var element : channelElements(deviceId, scope)) {
            var volume = getFloat(deviceId, PROP_VOLUME_SCALAR, scope, element);
            if (volume != null) {
                sum += volume;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    public void setVolume(int deviceId, int scope, float volume) {
        var clamped = Math.clamp(volume, 0, 1);
        var anySet = setFloat(deviceId, PROP_VOLUME_SCALAR, scope, ELEMENT_MAIN, clamped);
        if (!anySet) {
            for (var element : channelElements(deviceId, scope)) {
                anySet |= setFloat(deviceId, PROP_VOLUME_SCALAR, scope, element, clamped);
            }
        }
        if (!anySet) {
            warnMissingControl(PROP_VOLUME_SCALAR, deviceId, scope, "Device {} has no settable volume on scope {}");
        }
    }

    @Nullable
    public Boolean getMute(int deviceId, int scope) {
        var master = getInt(deviceId, PROP_MUTE, scope, ELEMENT_MAIN);
        if (master != null) {
            return master != 0;
        }
        for (var element : channelElements(deviceId, scope)) {
            var muted = getInt(deviceId, PROP_MUTE, scope, element);
            if (muted != null) {
                return muted != 0;
            }
        }
        return null;
    }

    public void setMute(int deviceId, int scope, boolean mute) {
        var anySet = setInt(deviceId, PROP_MUTE, scope, ELEMENT_MAIN, mute ? 1 : 0);
        if (!anySet) {
            for (var element : channelElements(deviceId, scope)) {
                anySet |= setInt(deviceId, PROP_MUTE, scope, element, mute ? 1 : 0);
            }
        }
        if (!anySet) {
            warnMissingControl(PROP_MUTE, deviceId, scope, "Device {} has no mute control on scope {}");
        }
    }

    /**
     * Channels to use when the main element (0, the master control) is missing or not settable, e.g. DisplayPort
     * monitors that only expose per-channel volume. Prefers the device's preferred stereo pair, falls back to 1/2.
     */
    private int[] channelElements(int deviceId, int scope) {
        if (hasProperty(deviceId, PROP_PREFERRED_STEREO_CHANNELS, scope, ELEMENT_MAIN)) {
            try (var data = new Memory(8)) {
                var size = new IntByReference(8);
                var status = ca().AudioObjectGetPropertyData(deviceId, address(PROP_PREFERRED_STEREO_CHANNELS, scope, ELEMENT_MAIN), 0, null, size, data);
                if (status == 0 && size.getValue() == 8) {
                    return new int[] { data.getInt(0), data.getInt(4) };
                }
            }
        }
        return FALLBACK_CHANNELS;
    }

    private void warnMissingControl(int selector, int deviceId, int scope, String message) {
        if (warnedMissingControls.add(selector + "/" + deviceId + "/" + scope)) {
            log.warn(message, deviceId, scope);
        } else {
            log.trace(message, deviceId, scope);
        }
    }

    public ListenerHandle addListener(int objectId, int selector, int scope, Runnable callback) {
        var address = address(selector, scope, ELEMENT_WILDCARD);
        CoreAudioLib.AudioObjectPropertyListenerProc proc = (objId, numberAddresses, addresses, clientData) -> {
            try {
                callback.run();
            } catch (RuntimeException e) {
                log.error("CoreAudio listener failed", e);
            }
            return 0;
        };
        var status = ca().AudioObjectAddPropertyListener(objectId, address, proc, null);
        if (status != 0) {
            log.warn("Unable to add CoreAudio listener for {} on {} ({})", selector, objectId, status);
            return null;
        }
        return new ListenerHandle(objectId, address, proc);
    }

    public void removeListener(@Nullable ListenerHandle handle) {
        if (handle == null) {
            return;
        }
        ca().AudioObjectRemovePropertyListener(handle.objectId(), handle.address(), handle.proc(), null);
    }

    private boolean hasProperty(int objectId, int selector, int scope, int element) {
        return ca().AudioObjectHasProperty(objectId, address(selector, scope, element)) != 0;
    }

    private boolean isSettable(int objectId, int selector, int scope, int element) {
        var settable = new ByteByReference();
        var status = ca().AudioObjectIsPropertySettable(objectId, address(selector, scope, element), settable);
        return status == 0 && settable.getValue() != 0;
    }

    private int streamCount(int deviceId, int scope) {
        var size = new IntByReference();
        var status = ca().AudioObjectGetPropertyDataSize(deviceId, address(PROP_STREAMS, scope, ELEMENT_MAIN), 0, null, size);
        return status == 0 ? size.getValue() / 4 : 0;
    }

    @Nullable
    private String getString(int objectId, int selector) {
        if (!hasProperty(objectId, selector, SCOPE_GLOBAL, ELEMENT_MAIN)) {
            return null;
        }
        try (var data = new Memory(Native.POINTER_SIZE)) {
            var size = new IntByReference(Native.POINTER_SIZE);
            var status = ca().AudioObjectGetPropertyData(objectId, address(selector, SCOPE_GLOBAL, ELEMENT_MAIN), 0, null, size, data);
            if (status != 0) {
                return null;
            }
            var stringPointer = data.getPointer(0);
            if (stringPointer == null) {
                return null;
            }
            var cfString = new CoreFoundation.CFStringRef(stringPointer);
            try {
                return cfString.stringValue();
            } finally {
                CoreFoundation.INSTANCE.CFRelease(cfString);
            }
        }
    }

    @Nullable
    private Float getFloat(int objectId, int selector, int scope, int element) {
        if (!hasProperty(objectId, selector, scope, element)) {
            return null;
        }
        try (var data = new Memory(4)) {
            var size = new IntByReference(4);
            var status = ca().AudioObjectGetPropertyData(objectId, address(selector, scope, element), 0, null, size, data);
            return status == 0 ? data.getFloat(0) : null;
        }
    }

    private boolean setFloat(int objectId, int selector, int scope, int element, float value) {
        if (!hasProperty(objectId, selector, scope, element) || !isSettable(objectId, selector, scope, element)) {
            return false;
        }
        try (var data = new Memory(4)) {
            data.setFloat(0, value);
            return ca().AudioObjectSetPropertyData(objectId, address(selector, scope, element), 0, null, 4, data) == 0;
        }
    }

    @Nullable
    private Integer getInt(int objectId, int selector, int scope, int element) {
        if (!hasProperty(objectId, selector, scope, element)) {
            return null;
        }
        try (var data = new Memory(4)) {
            var size = new IntByReference(4);
            var status = ca().AudioObjectGetPropertyData(objectId, address(selector, scope, element), 0, null, size, data);
            return status == 0 ? data.getInt(0) : null;
        }
    }

    private boolean setInt(int objectId, int selector, int scope, int element, int value) {
        if (!hasProperty(objectId, selector, scope, element) || !isSettable(objectId, selector, scope, element)) {
            return false;
        }
        try (var data = new Memory(4)) {
            data.setInt(0, value);
            return ca().AudioObjectSetPropertyData(objectId, address(selector, scope, element), 0, null, 4, data) == 0;
        }
    }

    private static CoreAudioLib.AudioObjectPropertyAddress address(int selector, int scope, int element) {
        return new CoreAudioLib.AudioObjectPropertyAddress(selector, scope, element);
    }

    static int fourcc(String code) {
        var bytes = code.getBytes(StandardCharsets.US_ASCII);
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF;
    }
}
