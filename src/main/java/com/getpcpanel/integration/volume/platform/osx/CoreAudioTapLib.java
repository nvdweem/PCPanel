package com.getpcpanel.integration.volume.platform.osx;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA binding to the CoreAudio process-tap API (macOS 14.4+) and the device IO-proc calls needed to
 * re-render tapped audio: tap a process ({@code AudioHardwareCreateProcessTap} with a
 * {@code CATapDescription} built via {@link ObjcFoundation}), wrap it in a private aggregate device and
 * play it back at an app-chosen gain. Complements {@link CoreAudioLib}, which covers the property API.
 */
public interface CoreAudioTapLib extends Library {
    CoreAudioTapLib INSTANCE = Native.load("/System/Library/Frameworks/CoreAudio.framework/CoreAudio", CoreAudioTapLib.class);

    /**
     * AudioDeviceIOProc: called on CoreAudio's realtime IO thread with the device's input data (for an
     * aggregate this includes the tapped audio) and the output buffers to fill. All buffer-list pointers
     * are raw {@code AudioBufferList*} parsed manually (see {@link ProcessTap}).
     */
    interface AudioDeviceIOProc extends Callback {
        @SuppressWarnings("unused") // Arguments dictated by CoreAudio
        int invoke(int inDevice, Pointer inNow, Pointer inInputData, Pointer inInputTime, Pointer outOutputData, Pointer inOutputTime, Pointer inClientData);
    }

    // OSStatus AudioHardwareCreateProcessTap(CATapDescription *inDescription, AudioObjectID *outTapID)
    int AudioHardwareCreateProcessTap(Pointer tapDescription, IntByReference outTapId);

    int AudioHardwareDestroyProcessTap(int tapId);

    // OSStatus AudioHardwareCreateAggregateDevice(CFDictionaryRef inDescription, AudioObjectID *outDeviceID)
    int AudioHardwareCreateAggregateDevice(Pointer description, IntByReference outDeviceId);

    int AudioHardwareDestroyAggregateDevice(int deviceId);

    int AudioDeviceCreateIOProcID(int deviceId, AudioDeviceIOProc proc, Pointer clientData, PointerByReference outProcId);

    int AudioDeviceDestroyIOProcID(int deviceId, Pointer procId);

    int AudioDeviceStart(int deviceId, Pointer procId);

    int AudioDeviceStop(int deviceId, Pointer procId);
}
