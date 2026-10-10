package dev.niels.pulse;

import java.time.Duration;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * What to record: a source by name ({@code null} the default; {@code @DEFAULT_MONITOR@} the default output's monitor),
 * or, with {@code monitoredStream} set, the sound of one playback stream (what {@code parec --monitor-stream} does). The
 * server converts to {@code spec} and hands the samples over in pieces of about {@code latency}. {@code properties}
 * describe the recording to other clients ({@code application.name} and the like).
 */
public record RecordRequest(@Nullable String source, int monitoredStream, SampleSpec spec, Duration latency, Map<String, String> properties) {
    private static final long INVALID_INDEX = 0xFFFFFFFFL;

    public static RecordRequest source(@Nullable String source, SampleSpec spec, Duration latency, Map<String, String> properties) {
        return new RecordRequest(source, -1, spec, latency, properties);
    }

    public static RecordRequest stream(int sinkInput, SampleSpec spec, Duration latency, Map<String, String> properties) {
        return new RecordRequest(null, sinkInput, spec, latency, properties);
    }

    /** The {@code CREATE_RECORD_STREAM} arguments in the layout of protocol {@code version} ({@code pulse/stream.c}). */
    void write(TagWriter w, int version) {
        var fragment = Math.max(spec.frameSize(), spec.rate() * spec.frameSize() * latency.toMillis() / 1000);
        w.putSampleSpec(spec)
         .putChannelMap(spec.channels())
         .putU32(INVALID_INDEX)
         .putString(source)
         .putU32(INVALID_INDEX) // maximum buffer length: the server's default
         .putBoolean(false) // corked
         .putU32(fragment);
        // Protocol 12: no remap, no remix, fix format/rate/channels, don't move, variable rate.
        for (var i = 0; i < 7; i++) {
            w.putBoolean(false);
        }
        w.putBoolean(false) // peak detection
         .putBoolean(true) // adjust latency: the fragment size is the latency asked for, as parec --latency-msec
         .putProplist(properties)
         .putU32(monitoredStream < 0 ? INVALID_INDEX : monitoredStream);
        w.putBoolean(false); // early requests (14)
        w.putBoolean(false).putBoolean(false); // don't inhibit auto suspend, fail on suspend (15)
        if (version >= 22) {
            w.putU8(0) // no formats: the sample spec decides
             .putCVolume(ChannelVolumes.uniform(spec.channels(), ChannelVolumes.NORM))
             .putBoolean(false) // muted
             .putBoolean(false) // volume set
             .putBoolean(false) // muted set
             .putBoolean(false) // relative volume
             .putBoolean(false); // passthrough
        }
    }
}
