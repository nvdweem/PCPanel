package com.getpcpanel.device.provider.deej;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.device.provider.deej.DeejProtocol.NoiseReduction;

@DisplayName("Deej protocol (pure, hardware-free)")
class DeejProtocolTest {

    // ── parse ────────────────────────────────────────────────────────────────

    @Test
    void parsesSingleSliderLine() {
        assertArrayEquals(new int[] { 512 }, DeejProtocol.parse("512\r\n"));
    }

    @Test
    void parsesFiveSliderLineWithCrlf() {
        assertArrayEquals(new int[] { 0, 240, 1023, 0, 483 }, DeejProtocol.parse("0|240|1023|0|483\r\n"));
    }

    @Test
    void parsesVaryingWidth() {
        assertArrayEquals(new int[] { 1, 2 }, DeejProtocol.parse("1|2\r\n"));
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 6, 7 }, DeejProtocol.parse("1|2|3|4|5|6|7\r\n"));
    }

    @Test
    void toleratesBareLf() {
        assertArrayEquals(new int[] { 100, 200 }, DeejProtocol.parse("100|200\n"));
    }

    @Test
    void toleratesNoLineTerminator() {
        assertArrayEquals(new int[] { 100, 200 }, DeejProtocol.parse("100|200"));
    }

    @Test
    void rejectsEmptyLine() {
        assertNull(DeejProtocol.parse(""));
        assertNull(DeejProtocol.parse("\r\n"));
        assertNull(DeejProtocol.parse("\n"));
    }

    @Test
    void rejectsNull() {
        assertNull(DeejProtocol.parse(null));
    }

    @Test
    void rejectsGarbage() {
        assertNull(DeejProtocol.parse("abc"));
        assertNull(DeejProtocol.parse("12|ab|34"));
        assertNull(DeejProtocol.parse("12.5|34"));
        assertNull(DeejProtocol.parse("-5|34"));
        assertNull(DeejProtocol.parse("|"));
        assertNull(DeejProtocol.parse("12|"));
        assertNull(DeejProtocol.parse("|12"));
    }

    @Test
    void rejectsPartialLine() {
        // A 5-digit run is not a valid \d{1,4} token (firmware never emits >1023).
        assertNull(DeejProtocol.parse("12345|6"));
    }

    @Test
    void clampsOutOfRangeFourDigitValue() {
        // 9999 passes the \d{1,4} regex but exceeds the 10-bit ADC range; clamp to 1023.
        assertArrayEquals(new int[] { 1023, 5 }, DeejProtocol.parse("9999|5\r\n"));
    }

    // ── normalize ──────────────────────────────────────────────────────────────

    @Test
    void normalizeBoundaries() {
        assertEquals(0, DeejProtocol.normalize(0));
        assertEquals(255, DeejProtocol.normalize(1023));
        // 512/1023*255 = 127.62 -> rounds to 128
        assertEquals(128, DeejProtocol.normalize(512));
    }

    @Test
    void normalizeClampsOutOfRange() {
        assertEquals(0, DeejProtocol.normalize(-10));
        assertEquals(255, DeejProtocol.normalize(5000));
    }

    // ── dead-band / significance ────────────────────────────────────────────────

    @Test
    void deadBandSuppressesSubThresholdDelta() {
        // default threshold 0.025; a delta of 0.01 (≈10/1023) must be suppressed.
        assertFalse(DeejProtocol.significantlyDifferent(0.5, 0.51, NoiseReduction.DEFAULT.threshold()));
    }

    @Test
    void deadBandPassesAtThreshold() {
        assertTrue(DeejProtocol.significantlyDifferent(0.5, 0.525, NoiseReduction.DEFAULT.threshold()));
    }

    @Test
    void deadBandAlwaysPassesTopRailSnap() {
        // arriving at exactly 1.0 from a sub-threshold-close value must still emit
        assertTrue(DeejProtocol.significantlyDifferent(0.999, 1.0, NoiseReduction.DEFAULT.threshold()));
    }

    @Test
    void deadBandAlwaysPassesBottomRailSnap() {
        assertTrue(DeejProtocol.significantlyDifferent(0.001, 0.0, NoiseReduction.DEFAULT.threshold()));
    }

    @Test
    void deadBandDoesNotReFireWhenAlreadyAtRail() {
        // already at the rail and staying there: not significant (no re-fire)
        assertFalse(DeejProtocol.significantlyDifferent(1.0, 1.0, NoiseReduction.DEFAULT.threshold()));
        assertFalse(DeejProtocol.significantlyDifferent(0.0, 0.0, NoiseReduction.DEFAULT.threshold()));
    }

    @Test
    void noiseReductionLevels() {
        assertEquals(0.015, NoiseReduction.LOW.threshold());
        assertEquals(0.025, NoiseReduction.DEFAULT.threshold());
        assertEquals(0.035, NoiseReduction.HIGH.threshold());
        assertEquals(NoiseReduction.DEFAULT, NoiseReduction.fromString(null));
        assertEquals(NoiseReduction.DEFAULT, NoiseReduction.fromString("garbage"));
        assertEquals(NoiseReduction.LOW, NoiseReduction.fromString("low"));
        assertEquals(NoiseReduction.HIGH, NoiseReduction.fromString("HIGH"));
    }

    @Test
    void significanceOnRawValues() {
        // raw delta of 5/1023 ≈ 0.0049 < 0.025 default -> suppressed
        assertFalse(DeejProtocol.significantlyDifferentRaw(500, 505, NoiseReduction.DEFAULT));
        // raw delta of 30/1023 ≈ 0.029 > 0.025 -> emitted
        assertTrue(DeejProtocol.significantlyDifferentRaw(500, 530, NoiseReduction.DEFAULT));
    }
}
