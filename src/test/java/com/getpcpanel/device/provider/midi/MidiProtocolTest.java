package com.getpcpanel.device.provider.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.midi.MidiProtocol.MidiKind;

@DisplayName("MIDI protocol (pure, hardware-free)")
class MidiProtocolTest {

    // ── normalize7bit: 0/64/127 -> 0/128/255 ───────────────────────────────────

    @Test
    void normalizeBoundaries() {
        assertEquals(0, MidiProtocol.normalize7bit(0));
        assertEquals(128, MidiProtocol.normalize7bit(64)); // 64*255/127 = 128.5 -> 128
        assertEquals(255, MidiProtocol.normalize7bit(127));
    }

    @Test
    void normalizeClampsOutOfRange() {
        assertEquals(0, MidiProtocol.normalize7bit(-5));
        assertEquals(255, MidiProtocol.normalize7bit(200));
    }

    // ── CONTROL_CHANGE -> ANALOG ───────────────────────────────────────────────

    @Test
    void controlChangeIsAnalog() {
        var e = MidiProtocol.decode(0xB0, 7, 100);
        assertEquals(MidiKind.ANALOG, e.kind());
        assertEquals(0, e.channel());
        assertEquals(7, e.number()); // CC number
        assertEquals(100, e.value()); // raw 0-127
    }

    @Test
    void controlChangeValueBoundaries() {
        assertEquals(0, MidiProtocol.decode(0xB0, 1, 0).value());
        assertEquals(64, MidiProtocol.decode(0xB0, 1, 64).value());
        assertEquals(127, MidiProtocol.decode(0xB0, 1, 127).value());
    }

    // ── NOTE_ON / NOTE_OFF -> BUTTON ───────────────────────────────────────────

    @Test
    void noteOnWithVelocityIsPress() {
        var e = MidiProtocol.decode(0x90, 36, 100);
        assertEquals(MidiKind.BUTTON, e.kind());
        assertEquals(36, e.number());
        assertTrue(e.pressed());
        assertEquals(100, e.value()); // velocity
    }

    @Test
    void noteOnVelocityZeroIsRelease() {
        var e = MidiProtocol.decode(0x90, 36, 0);
        assertEquals(MidiKind.BUTTON, e.kind());
        assertFalse(e.pressed());
    }

    @Test
    void noteOffIsRelease() {
        var e = MidiProtocol.decode(0x80, 36, 64);
        assertEquals(MidiKind.BUTTON, e.kind());
        assertFalse(e.pressed());
    }

    // ── channel extraction 0-15 ────────────────────────────────────────────────

    @Test
    void channelExtractedFromStatusNibble() {
        assertEquals(0, MidiProtocol.decode(0xB0, 7, 1).channel());
        assertEquals(9, MidiProtocol.decode(0xB9, 7, 1).channel());
        assertEquals(15, MidiProtocol.decode(0xBF, 7, 1).channel());
        assertEquals(9, MidiProtocol.decode(0x99, 36, 1).channel()); // NOTE_ON ch9 (drum channel)
    }

    // ── non-channel-voice / other messages ignored ────────────────────────────

    @Test
    void pitchBendIgnored() {
        assertNull(MidiProtocol.decode(0xE0, 0, 64)); // PITCH_BEND
    }

    @Test
    void programChangeIgnored() {
        assertNull(MidiProtocol.decode(0xC0, 5, 0)); // PROGRAM_CHANGE
    }

    @Test
    void channelPressureAndAftertouchIgnored() {
        assertNull(MidiProtocol.decode(0xD0, 64, 0)); // CHANNEL_PRESSURE
        assertNull(MidiProtocol.decode(0xA0, 36, 64)); // POLY_AFTERTOUCH
    }

    @Test
    void systemMessageIgnored() {
        assertNull(MidiProtocol.decode(0xF8, 0, 0)); // MIDI clock (system real-time)
    }

    @Test
    void onlyLow8BitsUsed() {
        // High bits beyond a byte are masked; data bytes masked to 7 bits.
        var e = MidiProtocol.decode(0xB0, 0xFF, 0xFF);
        assertEquals(127, e.number());
        assertEquals(127, e.value());
    }
}
