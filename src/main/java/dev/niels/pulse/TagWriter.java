package dev.niels.pulse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Builds a PulseAudio "tagstruct": every value is preceded by a one-byte type tag, numbers are big-endian. This is the
 * payload format of every control packet of the native protocol.
 */
public final class TagWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

    public TagWriter putU32(long value) {
        out.write(Tag.U32);
        rawU32(value);
        return this;
    }

    public TagWriter putU8(int value) {
        out.write(Tag.U8);
        out.write(value);
        return this;
    }

    public TagWriter putBoolean(boolean value) {
        out.write(value ? Tag.BOOLEAN_TRUE : Tag.BOOLEAN_FALSE);
        return this;
    }

    public TagWriter putString(@Nullable String value) {
        if (value == null) {
            out.write(Tag.STRING_NULL);
        } else {
            out.write(Tag.STRING);
            out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
            out.write(0);
        }
        return this;
    }

    public TagWriter putArbitrary(byte[] data) {
        out.write(Tag.ARBITRARY);
        rawU32(data.length);
        out.writeBytes(data);
        return this;
    }

    public TagWriter putCVolume(ChannelVolumes volumes) {
        out.write(Tag.CVOLUME);
        out.write(volumes.channels());
        for (var i = 0; i < volumes.channels(); i++) {
            rawU32(volumes.get(i));
        }
        return this;
    }

    /** Values are written as NUL-terminated strings, which is how PulseAudio stores textual properties. */
    public TagWriter putProplist(Map<String, String> properties) {
        out.write(Tag.PROPLIST);
        properties.forEach((key, value) -> {
            var bytes = (value + '\0').getBytes(StandardCharsets.UTF_8);
            putString(key);
            putU32(bytes.length);
            putArbitrary(bytes);
        });
        putString(null);
        return this;
    }

    /** Bytes written as they are, for fields this writer has no typed method for. */
    TagWriter putRaw(byte... bytes) {
        out.writeBytes(bytes);
        return this;
    }

    private void rawU32(long value) {
        out.write((int) (value >>> 24));
        out.write((int) (value >>> 16));
        out.write((int) (value >>> 8));
        out.write((int) value);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
