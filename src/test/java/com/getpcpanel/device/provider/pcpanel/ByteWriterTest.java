package com.getpcpanel.device.provider.pcpanel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the pure RGB/brightness byte encoding that {@link ByteWriter} produces for HID lighting
 * commands — the exact bytes written to the device over hid4java. No hardware needed: this is the
 * encoder, not the transport.
 */
@DisplayName("HID lighting byte encoding (ByteWriter)")
class ByteWriterTest {

    private static int u(byte b) {
        return b & 0xFF;
    }

    @Test
    @DisplayName("appendHex parses #rrggbb into RGB bytes at full brightness")
    void appendHexParsesColor() {
        var out = new ByteWriter(100, 8).appendHex("#FF8000").get();
        assertEquals(0xFF, u(out[0]));
        assertEquals(0x80, u(out[1]));
        assertEquals(0x00, u(out[2]));
    }

    @Test
    @DisplayName("appendHex accepts the form without a leading #")
    void appendHexWithoutHash() {
        var out = new ByteWriter(100, 8).appendHex("00ff10").get();
        assertEquals(0x00, u(out[0]));
        assertEquals(0xFF, u(out[1]));
        assertEquals(0x10, u(out[2]));
    }

    @ParameterizedTest
    @ValueSource(strings = { "nothex", "#gg0000", "#ff", "", "#" })
    @DisplayName("invalid hex falls back to black instead of throwing")
    void invalidHexIsBlack(String hex) {
        var out = new ByteWriter(100, 8).appendHex(hex).get();
        assertEquals(0, u(out[0]) | u(out[1]) | u(out[2]));
    }

    @Test
    @DisplayName("null hex falls back to black")
    void nullHexIsBlack() {
        var out = new ByteWriter(100, 8).appendHex(null).get();
        assertEquals(0, u(out[0]) | u(out[1]) | u(out[2]));
    }

    @Test
    @DisplayName("appendRGB clamps components to [0,255]")
    void appendRgbClamps() {
        var out = new ByteWriter(100, 8).appendRGB(300, -10, 128).get();
        assertEquals(255, u(out[0]));
        assertEquals(0, u(out[1]));
        assertEquals(128, u(out[2]));
    }

    @Test
    @DisplayName("brightness scales each channel linearly (50% halves)")
    void brightnessScales() {
        var out = new ByteWriter(50, 8).appendRGB(200, 100, 0).get();
        assertEquals(100, u(out[0]));
        assertEquals(50, u(out[1]));
        assertEquals(0, u(out[2]));
    }

    @Test
    @DisplayName("zero brightness turns every channel off")
    void zeroBrightnessIsOff() {
        var out = new ByteWriter(0, 8).appendHex("#ffffff").get();
        assertEquals(0, u(out[0]) | u(out[1]) | u(out[2]));
    }

    @Test
    @DisplayName("append advances the write position so colours pack sequentially")
    void appendAdvancesPosition() {
        var out = new ByteWriter(100, 8).appendRGB(1, 2, 3).appendRGB(4, 5, 6).get();
        assertEquals(1, u(out[0]));
        assertEquals(2, u(out[1]));
        assertEquals(3, u(out[2]));
        assertEquals(4, u(out[3]));
        assertEquals(5, u(out[4]));
        assertEquals(6, u(out[5]));
    }
}
