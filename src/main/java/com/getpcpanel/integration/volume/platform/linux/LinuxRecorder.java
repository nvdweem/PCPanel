package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import dev.niels.pulse.PulseException;
import dev.niels.pulse.PulseTimeoutException;
import dev.niels.pulse.RecordRequest;
import dev.niels.pulse.RecordStream;
import dev.niels.pulse.SampleSpec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Records mono 32-bit float audio for the visualizer and the level meters: over the app's protocol connection
 * ({@link PulseConnection}) while there is one, otherwise through {@code parec} (pulseaudio-utils). The sound server
 * does the downmix and the rate change either way.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxRecorder {
    @Inject ProcessHelper processes;
    @Inject PulseConnection pulse;

    @Nullable private Boolean parecRuns;

    /**
     * What to record: a source by name ({@code @DEFAULT_MONITOR@}, {@code @DEFAULT_SOURCE@}, a sink's
     * {@code <name>.monitor}, an input's own name), or the sound of one playback stream.
     */
    record Target(@Nullable String source, int stream) {
        static Target source(String source) {
            return new Target(source, -1);
        }

        static Target stream(int sinkInput) {
            return new Target(null, sinkInput);
        }

        /** The parec argument that records the same. */
        String parecArgument() {
            return stream >= 0 ? "--monitor-stream=" + stream : "--device=" + source;
        }

        @Override
        public String toString() {
            return stream >= 0 ? "stream " + stream : String.valueOf(source);
        }
    }

    /** A running recording. */
    interface Recording {
        boolean isAlive();

        void stop();
    }

    /** Whether recording can work at all: there is a protocol connection, or parec runs. */
    boolean available() {
        if (pulse.client() != null) {
            return true;
        }
        if (parecRuns == null) {
            parecRuns = parecRuns();
        }
        return parecRuns;
    }

    private boolean parecRuns() {
        try {
            return processes.run(Duration.ofSeconds(3), "parec", "--version").succeeded();
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Starts recording {@code target} at {@code rate}, handing {@code samples} pieces of about {@code latencyMs} each on a
     * thread of its own. {@code clientName} is the recording's {@code application.name}, which is how the microphone
     * alert tells it apart from a real recording. {@code null} when it could not start.
     */
    @Nullable
    Recording start(Target target, String clientName, int rate, int latencyMs, Consumer<FloatBuffer> samples) {
        var client = pulse.client();
        if (client != null) {
            var request = target.stream() >= 0
                    ? RecordRequest.stream(target.stream(), SampleSpec.float32Mono(rate), Duration.ofMillis(latencyMs), properties(clientName))
                    : RecordRequest.source(target.source(), SampleSpec.float32Mono(rate), Duration.ofMillis(latencyMs), properties(clientName));
            try {
                return new ProtocolRecording(client.record(request, data -> samples.accept(data.asFloatBuffer())));
            } catch (PulseTimeoutException e) {
                // PipeWire answers once the stream is linked, which a source that is slow to wake (a Bluetooth headset)
                // can take longer than the timeout for. The connection is fine and shared with everything else: keep it,
                // and let the caller try again later.
                log.debug("Recording {} did not start in time: {}", target, e.getMessage());
                return null;
            } catch (PulseException e) {
                if (e.isServerError()) {
                    // The source or stream is not there (any more); parec would be told the same.
                    log.debug("Unable to record {}: {}", target, e.getMessage());
                    return null;
                }
                pulse.lost(client, e);
            }
        }
        return startParec(target, clientName, rate, latencyMs, samples);
    }

    private static Map<String, String> properties(String clientName) {
        return Map.of("application.name", clientName, "media.name", clientName);
    }

    @Nullable
    private Recording startParec(Target target, String clientName, int rate, int latencyMs, Consumer<FloatBuffer> samples) {
        try {
            var process = processes.startReading(parecCommand(target, clientName, rate, latencyMs).toArray(String[]::new));
            var chunk = Math.max(1, rate * latencyMs / 1000) * Float.BYTES;
            var reader = new Thread(() -> readParec(process.getInputStream(), chunk, samples), "parec " + target);
            reader.setDaemon(true);
            reader.start();
            return new ParecRecording(process);
        } catch (IOException e) {
            log.debug("Unable to start parec for {}: {}", target, e.toString());
            return null;
        }
    }

    static List<String> parecCommand(Target target, String clientName, int rate, int latencyMs) {
        return List.of("parec", target.parecArgument(), "--client-name=" + clientName, "--format=float32le", "--channels=1", "--rate=" + rate, "--raw",
                "--latency-msec=" + latencyMs);
    }

    static void readParec(InputStream in, int chunk, Consumer<FloatBuffer> samples) {
        var bytes = new byte[chunk];
        try (in) {
            int n;
            // Whole chunks, so a read never splits a sample.
            while ((n = in.readNBytes(bytes, 0, chunk)) > 0) {
                samples.accept(ByteBuffer.wrap(bytes, 0, n - n % Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer());
            }
        } catch (IOException e) {
            // the recording was stopped
        }
    }

    private record ProtocolRecording(RecordStream stream) implements Recording {
        @Override
        public boolean isAlive() {
            return stream.isOpen();
        }

        @Override
        public void stop() {
            stream.close();
        }
    }

    private record ParecRecording(Process process) implements Recording {
        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public void stop() {
            ProcessHelper.stop(process);
        }
    }
}
