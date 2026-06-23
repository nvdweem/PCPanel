package com.getpcpanel.device.io;

import java.util.List;
import java.util.function.Consumer;

/**
 * A mockable MIDI transport seam. Production code uses a {@code javax.sound.midi}-backed
 * implementation ({@code JavaxMidiTransport}); tests use an in-memory fake that feeds canned
 * messages, so the MIDI provider can be exercised end-to-end without any real MIDI device.
 *
 * <p>The transport is a thin adapter: it enumerates MIDI input devices and opens one, delivering
 * each inbound message as the three raw bytes of a {@code ShortMessage} ({@code status},
 * {@code data1}, {@code data2}) to a consumer. All decoding/normalization stays in
 * {@link com.getpcpanel.device.provider.midi.MidiProtocol} — the impl never interprets a message.
 */
public interface MidiTransport {
    /**
     * Lists the MIDI input devices currently available on the system. Best-effort: a backend that is
     * unavailable (e.g. {@code javax.sound.midi} broken in a native image) returns an empty list
     * rather than throwing.
     */
    List<MidiDeviceInfo> listInputs();

    /**
     * Opens the input device identified by {@code id} and delivers each inbound channel-voice message
     * (status, data1, data2) to {@code onMessage}. {@code onError} <em>may</em> be invoked if the device
     * surfaces an error, but it is best-effort and backend-dependent — the {@code javax.sound.midi}
     * backend has no loss-of-device callback and never fires it, so the provider detects unplug by
     * periodic enumerate-and-reconcile rather than relying on {@code onError}. Returns an open
     * {@link MidiConnection}.
     */
    MidiConnection open(String id, Consumer<MidiMessage> onMessage, Consumer<Throwable> onError);

    /** A MIDI input device's identity for discovery + persistence. {@code id} is stable per device. */
    record MidiDeviceInfo(String id, String name) {
    }

    /** The three raw bytes of an inbound {@code ShortMessage}. */
    record MidiMessage(int status, int data1, int data2) {
    }

    /** An open MIDI input connection. Idempotent {@link #close()}. */
    interface MidiConnection extends AutoCloseable {
        String deviceId();

        boolean isOpen();

        @Override
        void close();
    }
}
