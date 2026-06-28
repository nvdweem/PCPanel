package com.getpcpanel.integration.volume.platform.osx;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.IntByReference;

/**
 * Minimal JNA binding to the CoreAudio HAL (AudioObject API) used to enumerate audio devices,
 * read/write volume and mute, switch default devices and listen for changes.
 */
public interface CoreAudioLib extends Library {
    CoreAudioLib INSTANCE = Native.load("/System/Library/Frameworks/CoreAudio.framework/CoreAudio", CoreAudioLib.class);

    int kAudioObjectSystemObject = 1;

    @Structure.FieldOrder({ "mSelector", "mScope", "mElement" })
    class AudioObjectPropertyAddress extends Structure {
        public int mSelector;
        public int mScope;
        public int mElement;

        public AudioObjectPropertyAddress() {
        }

        public AudioObjectPropertyAddress(int selector, int scope, int element) {
            mSelector = selector;
            mScope = scope;
            mElement = element;
        }
    }

    interface AudioObjectPropertyListenerProc extends Callback {
        @SuppressWarnings("unused") // Arguments dictated by CoreAudio
        int invoke(int objectId, int numberAddresses, Pointer addresses, Pointer clientData);
    }

    int AudioObjectGetPropertyDataSize(int objectId, AudioObjectPropertyAddress address, int qualifierDataSize, Pointer qualifierData, IntByReference dataSize);

    int AudioObjectGetPropertyData(int objectId, AudioObjectPropertyAddress address, int qualifierDataSize, Pointer qualifierData, IntByReference dataSize, Pointer data);

    int AudioObjectSetPropertyData(int objectId, AudioObjectPropertyAddress address, int qualifierDataSize, Pointer qualifierData, int dataSize, Pointer data);

    byte AudioObjectHasProperty(int objectId, AudioObjectPropertyAddress address);

    int AudioObjectIsPropertySettable(int objectId, AudioObjectPropertyAddress address, ByteByReference settable);

    int AudioObjectAddPropertyListener(int objectId, AudioObjectPropertyAddress address, AudioObjectPropertyListenerProc listener, Pointer clientData);

    int AudioObjectRemovePropertyListener(int objectId, AudioObjectPropertyAddress address, AudioObjectPropertyListenerProc listener, Pointer clientData);
}
