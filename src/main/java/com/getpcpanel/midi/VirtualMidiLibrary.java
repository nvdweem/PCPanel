package com.getpcpanel.midi;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;

public interface VirtualMidiLibrary extends Library {
    VirtualMidiLibrary INSTANCE = Native.load("teVirtualMIDI", VirtualMidiLibrary.class);

    // Error codes
    int TE_VM_ERROR_BUSY = 170;
    int TE_VM_ERROR_ALREADY_EXISTS = 183;

    // Creation flags
    int TE_VM_FLAGS_PARSE_RX = 1;
    int TE_VM_FLAGS_PARSE_TX = 2;
    int TE_VM_FLAGS_INSTANTIATE_RX = 4;
    int TE_VM_FLAGS_INSTANTIATE_TX = 8;
    int TE_VM_FLAGS_INSTANTIATE_BOTH = 12;

    interface Callback extends com.sun.jna.Callback {
        void callback(Pointer midiPort, Pointer data, int length, Pointer clientContext);
    }

    WString virtualMIDIGetVersion(IntByReference major, IntByReference minor, IntByReference release, IntByReference build);

    Pointer virtualMIDICreatePortEx2(WString portName, Callback callback, Pointer clientContext, int maxSysexLength, int flags);

    void virtualMIDIClosePort(Pointer instance);

    boolean virtualMIDISendData(Pointer instance, byte[] data, int length);

    boolean virtualMIDIGetData(Pointer instance, byte[] buffer, IntByReference length);

    boolean virtualMIDIShutdown(Pointer instance);

    int virtualMIDIGetLastError();
}
