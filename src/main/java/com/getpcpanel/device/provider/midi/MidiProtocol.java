package com.getpcpanel.device.provider.midi;

import javax.annotation.Nullable;

/**
 * Pure, hardware-free decoding of MIDI channel-voice messages into a normalized event model. All
 * methods are static and side-effect free and take the three raw bytes of a {@code ShortMessage}
 * ({@code status}, {@code data1}, {@code data2}) as plain ints, so the protocol can be unit-tested
 * by feeding canned messages with no {@code javax.sound.midi} types and no real MIDI device.
 *
 * <p>MIDI channel-voice data bytes are 7-bit (0-127). This maps:
 * <ul>
 *   <li>CONTROL_CHANGE (0xB0) -&gt; an {@link MidiKind#ANALOG} control: {@code number}=CC number,
 *       {@code value}=data2 (0-127).
 *   <li>NOTE_ON (0x90) with velocity &gt; 0 -&gt; a {@link MidiKind#BUTTON} press: {@code number}=note,
 *       {@code value}=velocity.
 *   <li>NOTE_ON velocity 0 (running-status note-off) or NOTE_OFF (0x80) -&gt; a button release.
 * </ul>
 * Any other message (pitch-bend, program-change, channel-pressure, aftertouch, system messages, ...)
 * is ignored: {@link #decode} returns {@code null} so the caller can cleanly skip it.
 */
public final class MidiProtocol {
    /** Max raw value of a 7-bit MIDI data byte. */
    public static final int RAW_MAX = 127;
    /** Canonical internal analog domain max (shared with PCPanel/Deej). */
    public static final int NORMALIZED_MAX = 255;

    // MIDI status-byte command nibbles (the high nibble; the low nibble is the channel).
    private static final int CMD_NOTE_OFF = 0x80;
    private static final int CMD_NOTE_ON = 0x90;
    private static final int CMD_CONTROL_CHANGE = 0xB0;

    private MidiProtocol() {
    }

    /** The form of a decoded MIDI control event. */
    public enum MidiKind {
        ANALOG, BUTTON
    }

    /**
     * A decoded, hardware-free MIDI control event. {@code channel} is 0-15. For {@code ANALOG}
     * (a CONTROL_CHANGE) {@code number} is the CC number and {@code value} the raw 0-127 value;
     * {@code pressed} is unused. For {@code BUTTON} (a NOTE_ON/NOTE_OFF) {@code number} is the note,
     * {@code value} the raw velocity (0 on release) and {@code pressed} the press/release state.
     */
    public record MidiEvent(MidiKind kind, int channel, int number, int value, boolean pressed) {
    }

    /**
     * Decodes one raw MIDI message into a {@link MidiEvent}, or {@code null} for any message that is
     * not a CONTROL_CHANGE / NOTE_ON / NOTE_OFF. Only the low 8 bits of each argument are used.
     */
    @Nullable
    public static MidiEvent decode(int status, int data1, int data2) {
        var command = status & 0xF0;
        var channel = status & 0x0F;
        var number = data1 & 0x7F;
        var value = data2 & 0x7F;
        return switch (command) {
            case CMD_CONTROL_CHANGE -> new MidiEvent(MidiKind.ANALOG, channel, number, value, false);
            case CMD_NOTE_ON -> new MidiEvent(MidiKind.BUTTON, channel, number, value, value > 0);
            case CMD_NOTE_OFF -> new MidiEvent(MidiKind.BUTTON, channel, number, value, false);
            default -> null;
        };
    }

    /**
     * Normalizes a raw 7-bit (0-127) value to the canonical 0-255 domain. Uses truncation rather than
     * round-to-nearest so the midpoint maps cleanly: 0 -&gt; 0, 64 -&gt; 128, 127 -&gt; 255. (Round-half-up
     * would push the midpoint to 129; the 1-LSB difference is immaterial for volume but truncation
     * keeps the documented boundary mapping exact.)
     */
    public static int normalize7bit(int v0to127) {
        var clamped = Math.max(0, Math.min(RAW_MAX, v0to127));
        return clamped * NORMALIZED_MAX / RAW_MAX;
    }
}
