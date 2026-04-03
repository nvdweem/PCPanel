package com.getpcpanel.hid;

import java.util.stream.Stream;

/**
 * Builds byte arrays for HID lighting commands.
 * Colors are passed as hex strings (e.g. "#rrggbb") or as separate R/G/B int components.
 */
class ByteWriter {
    private final byte[] buff;
    private final int brightnessMultiplier;
    private int pos;
    private int marked;

    public ByteWriter(int brightness) {
        this(brightness, 64);
    }

    public ByteWriter(int brightness, int length) {
        buff = new byte[length];
        brightnessMultiplier = brightness;
    }

    public ByteWriter append(Number... bytes) {
        return append(Stream.of(bytes).map(Number::byteValue).toArray(Byte[]::new));
    }

    public ByteWriter append(Byte... bytes) {
        for (var i = 0; i < bytes.length; i++) {
            buff[pos + i] = bytes[i];
        }
        return skip(bytes.length);
    }

    public ByteWriter appendBrightness(byte nr) {
        return append(applyBrightness(nr));
    }

    /**
     * Append RGB from a CSS hex color string ("#rrggbb"). If null/invalid, appends black.
     */
    public ByteWriter appendHex(String hexColor) {
        int r = 0, g = 0, b = 0;
        if (hexColor != null) {
            try {
                String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
                r = Integer.parseInt(hex.substring(0, 2), 16) & 0xFF;
                g = Integer.parseInt(hex.substring(2, 4), 16) & 0xFF;
                b = Integer.parseInt(hex.substring(4, 6), 16) & 0xFF;
            } catch (Exception ignored) {
            }
        }
        return append(applyBrightness((byte) r), applyBrightness((byte) g), applyBrightness((byte) b));
    }

    /**
     * Append RGB from int components (0-255). Values are clamped to [0, 255].
     */
    public ByteWriter appendRGB(int r, int g, int b) {
        return append(
            applyBrightness((byte) (Math.min(255, Math.max(0, r)) & 0xFF)),
            applyBrightness((byte) (Math.min(255, Math.max(0, g)) & 0xFF)),
            applyBrightness((byte) (Math.min(255, Math.max(0, b)) & 0xFF))
        );
    }

    private byte applyBrightness(byte nr) {
        //noinspection NumericCastThatLosesPrecision
        return (byte) (brightnessMultiplier / 100f * (nr & 0xff));
    }

    public ByteWriter skip(int len) {
        pos += len;
        return this;
    }

    public void skipFromMark(int len) {
        skip(len - (pos - marked));
    }

    public byte[] get() {
        return buff;
    }

    public ByteWriter mark() {
        marked = pos;
        return this;
    }
}
