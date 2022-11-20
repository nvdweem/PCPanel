package com.getpcpanel.hid;

import java.util.stream.Stream;

import javafx.scene.paint.Color;

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

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public ByteWriter append(Color c) {
        return append(applyBrightness((byte) (c.getRed() * 255)), applyBrightness((byte) (c.getGreen() * 255)), applyBrightness((byte) (c.getBlue() * 255)));
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
