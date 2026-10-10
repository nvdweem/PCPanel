package dev.niels.pulse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.niels.pulse.model.DeviceInfo;
import dev.niels.pulse.model.ServerInfo;
import dev.niels.pulse.model.StreamInfo;

/**
 * Decodes the introspection replies. Their layout grows with the protocol version: the server writes the fields of
 * the version the connection negotiated, so each reader takes that version and reads exactly those
 * ({@code pulse/introspect.c} on the client, {@code pulsecore/protocol-native.c} on the server).
 */
final class Replies {
    private Replies() {
    }

    static <T> List<T> list(TagReader reader, Function<TagReader, T> entry) {
        var result = new ArrayList<T>();
        while (reader.hasMore()) {
            result.add(entry.apply(reader));
        }
        return result;
    }

    static ServerInfo serverInfo(TagReader r) {
        var serverName = r.getString();
        var serverVersion = r.getString();
        var userName = r.getString();
        var hostName = r.getString();
        r.getSampleSpec();
        var defaultSink = r.getString();
        var defaultSource = r.getString();
        // The cookie and (v15) the default channel map follow; nothing here uses them.
        return new ServerInfo(userName, hostName, serverVersion, serverName, defaultSink, defaultSource);
    }

    /** A sink, or with {@code sink} false a source: the two differ only in where the format list starts. */
    static DeviceInfo device(TagReader r, int version, boolean sink) {
        var index = r.getIndex();
        var name = r.getString();
        var description = r.getString();
        r.getSampleSpec();
        r.getChannelMap();
        r.getIndex(); // owner module
        var volume = r.getCVolume();
        var muted = r.getBoolean();
        var monitor = r.getIndex();
        var monitorName = r.getString();
        r.getUsec(); // latency
        var driver = r.getString();
        r.getU32(); // flags
        Map<String, String> properties = Map.of();
        if (version >= 13) {
            properties = r.getProplist();
            r.getUsec(); // configured latency
        }
        if (version >= 15) {
            r.getVolume(); // base volume
            r.getU32(); // state
            r.getU32(); // volume steps
            r.getIndex(); // card
        }
        String activePort = null;
        if (version >= 16) {
            var ports = r.getU32();
            for (var i = 0; i < ports; i++) {
                r.getString(); // name
                r.getString(); // description
                r.getU32(); // priority
                if (version >= 24) {
                    r.getU32(); // available
                }
                if (version >= 34) {
                    r.getString(); // availability group
                    r.getU32(); // type
                }
            }
            activePort = r.getString();
        }
        if (version >= (sink ? 21 : 22)) {
            var formats = r.getU8();
            for (var i = 0; i < formats; i++) {
                r.skipFormatInfo();
            }
        }
        return new DeviceInfo(index, name, description, volume, muted, monitor, monitorName, driver, properties, activePort);
    }

    static StreamInfo sinkInput(TagReader r, int version) {
        var index = r.getIndex();
        var name = r.getString();
        r.getIndex(); // owner module
        var client = r.getIndex();
        var sink = r.getIndex();
        r.getSampleSpec();
        r.getChannelMap();
        var volume = r.getCVolume();
        r.getUsec(); // buffer latency
        r.getUsec(); // sink latency
        r.getString(); // resample method
        var driver = r.getString();
        var muted = version >= 11 && r.getBoolean();
        var properties = version >= 13 ? r.getProplist() : Map.<String, String>of();
        var corked = version >= 19 && r.getBoolean();
        if (version >= 20) {
            r.getBoolean(); // has volume
            r.getBoolean(); // volume writable
        }
        if (version >= 21) {
            r.skipFormatInfo();
        }
        return new StreamInfo(index, name, client, sink, volume, muted, corked, driver, properties);
    }

    static StreamInfo sourceOutput(TagReader r, int version) {
        var index = r.getIndex();
        var name = r.getString();
        r.getIndex(); // owner module
        var client = r.getIndex();
        var source = r.getIndex();
        r.getSampleSpec();
        r.getChannelMap();
        r.getUsec(); // buffer latency
        r.getUsec(); // source latency
        r.getString(); // resample method
        var driver = r.getString();
        var properties = version >= 13 ? r.getProplist() : Map.<String, String>of();
        var corked = version >= 19 && r.getBoolean();
        ChannelVolumes volume = null;
        var muted = false;
        if (version >= 22) {
            volume = r.getCVolume();
            muted = r.getBoolean();
            r.getBoolean(); // has volume
            r.getBoolean(); // volume writable
            r.skipFormatInfo();
        }
        return new StreamInfo(index, name, client, source, volume, muted, corked, driver, properties);
    }
}
