package dev.niels.pulse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Reads a tagstruct (see {@link TagWriter}). Every read checks the type tag, so a reply that does not have the expected
 * shape fails with a {@link PulseProtocolException} instead of being read as garbage.
 */
public final class TagReader {
    private final ByteBuffer buffer;

    public TagReader(byte[] data) {
        buffer = ByteBuffer.wrap(data);
    }

    public boolean hasMore() {
        return buffer.hasRemaining();
    }

    public long getU32() {
        expect(Tag.U32);
        return rawU32();
    }

    /** A u32 that is an index or a count, where {@code 0xFFFFFFFF} (PA_INVALID_INDEX) becomes {@code -1}. */
    public int getIndex() {
        return (int) getU32();
    }

    public int getU8() {
        expect(Tag.U8);
        return rawU8();
    }

    public long getUsec() {
        expect(Tag.USEC);
        return buffer.getLong();
    }

    public long getVolume() {
        expect(Tag.VOLUME);
        return rawU32();
    }

    public boolean getBoolean() {
        var tag = rawU8();
        if (tag == Tag.BOOLEAN_TRUE) {
            return true;
        }
        if (tag == Tag.BOOLEAN_FALSE) {
            return false;
        }
        throw unexpected(tag, "boolean");
    }

    @Nullable
    public String getString() {
        var tag = rawU8();
        if (tag == Tag.STRING_NULL) {
            return null;
        }
        if (tag != Tag.STRING) {
            throw unexpected(tag, "string");
        }
        var start = buffer.position();
        var end = start;
        while (end < buffer.limit() && buffer.get(end) != 0) {
            end++;
        }
        if (end == buffer.limit()) {
            throw new PulseProtocolException("Unterminated string");
        }
        var value = new String(buffer.array(), start, end - start, StandardCharsets.UTF_8);
        buffer.position(end + 1);
        return value;
    }

    public byte[] getArbitrary() {
        expect(Tag.ARBITRARY);
        var length = rawU32();
        if (length > buffer.remaining()) {
            throw new PulseProtocolException("Arbitrary data of " + length + " bytes exceeds the packet");
        }
        var data = new byte[(int) length];
        buffer.get(data);
        return data;
    }

    public SampleSpec getSampleSpec() {
        expect(Tag.SAMPLE_SPEC);
        var format = rawU8();
        var channels = rawU8();
        return new SampleSpec(format, channels, rawU32());
    }

    /** The channel positions, which the callers here do not use beyond the count. */
    public int getChannelMap() {
        expect(Tag.CHANNEL_MAP);
        var channels = rawU8();
        buffer.position(buffer.position() + channels);
        return channels;
    }

    public ChannelVolumes getCVolume() {
        expect(Tag.CVOLUME);
        var channels = rawU8();
        var values = new long[channels];
        for (var i = 0; i < channels; i++) {
            values[i] = rawU32();
        }
        return new ChannelVolumes(values);
    }

    /** A property list; values are decoded as text without their trailing NUL, the way {@code pactl} prints them. */
    public Map<String, String> getProplist() {
        expect(Tag.PROPLIST);
        var properties = new LinkedHashMap<String, String>();
        while (true) {
            var key = getString();
            if (key == null) {
                return properties;
            }
            var length = getU32();
            var data = getArbitrary();
            if (data.length != length) {
                throw new PulseProtocolException("Property " + key + " announces " + length + " bytes but carries " + data.length);
            }
            var textLength = data.length > 0 && data[data.length - 1] == 0 ? data.length - 1 : data.length;
            properties.put(key, new String(data, 0, textLength, StandardCharsets.UTF_8));
        }
    }

    /** A stream or device format (encoding + properties), skipped: nothing here needs it. */
    public void skipFormatInfo() {
        expect(Tag.FORMAT_INFO);
        getU8();
        getProplist();
    }

    private void expect(int tag) {
        var actual = rawU8();
        if (actual != tag) {
            throw unexpected(actual, String.valueOf((char) tag));
        }
    }

    private PulseProtocolException unexpected(int tag, String expected) {
        return new PulseProtocolException("Expected tag '" + expected + "' at offset " + (buffer.position() - 1) + " but found '" + (char) tag + "'");
    }

    private int rawU8() {
        if (!buffer.hasRemaining()) {
            throw new PulseProtocolException("Unexpected end of packet");
        }
        return Byte.toUnsignedInt(buffer.get());
    }

    private long rawU32() {
        if (buffer.remaining() < 4) {
            throw new PulseProtocolException("Unexpected end of packet");
        }
        return Integer.toUnsignedLong(buffer.getInt());
    }
}
