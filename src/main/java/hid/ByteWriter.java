package hid;

import java.util.stream.Stream;

import javafx.scene.paint.Color;

public class ByteWriter {
    private final byte[] buff;
    private int pos;
    private int marked;

    public ByteWriter() {
        this(64);
    }

    public ByteWriter(int length) {
        buff = new byte[length];
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

    @SuppressWarnings("NumericCastThatLosesPrecision")
    public ByteWriter append(Color c) {
        return append((byte) (c.getRed() * 255), (byte) (c.getGreen() * 255), (byte) (c.getBlue() * 255));
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
