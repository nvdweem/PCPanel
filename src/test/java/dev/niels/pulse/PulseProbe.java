package dev.niels.pulse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import dev.niels.pulse.PulseClient.DeviceRef;
import dev.niels.pulse.model.SubscriptionEvent.Facility;

/**
 * Prints what the local server reports, to compare with {@code pactl list}; with {@code watch <seconds>} it also prints
 * the subscription events, and with {@code bench <sink-name> <count>} it times volume writes.
 */
public final class PulseProbe {
    private PulseProbe() {
    }

    public static void main(String[] args) throws Exception {
        var env = System.getenv();
        var socket = PulseServerLocator.socket(env).orElseThrow(() -> new IllegalStateException("No pulse socket"));
        try (var client = PulseClient.connect(socket, PulseServerLocator.cookie(env, Path.of(System.getProperty("user.home"))),
                Map.of("application.name", "PulseProbe"), Duration.ofSeconds(2))) {
            System.out.println("socket " + socket + ", protocol " + client.version() + " (server " + client.serverVersion() + ")");
            System.out.println(client.serverInfo());
            client.sinks().forEach(s -> System.out.println("sink " + s));
            client.sources().forEach(s -> System.out.println("source " + s));
            client.sinkInputs().forEach(s -> System.out.println("sink-input " + s));
            client.sourceOutputs().forEach(s -> System.out.println("source-output " + s));
            System.out.println("default sink " + client.sink(DeviceRef.DEFAULT_SINK).name());
            if (args.length >= 2 && args[0].equals("watch")) {
                client.subscribe(Set.of(Facility.values()), e -> System.out.println("Event '" + e.type().pactlName() + "' on " + e.facility().pactlName() + " #" + e.index()));
                Thread.sleep(Long.parseLong(args[1]) * 1000);
            }
            if (args.length >= 3 && args[0].equals("rec")) {
                var spec = SampleSpec.float32Mono(8000);
                var properties = Map.of("application.name", "PulseProbe recorder");
                var request = args[1].startsWith("stream:")
                        ? RecordRequest.stream(Integer.parseInt(args[1].substring(7)), spec, Duration.ofMillis(20), properties)
                        : RecordRequest.source(args[1].equals("default") ? null : args[1], spec, Duration.ofMillis(20), properties);
                var samples = new java.util.concurrent.atomic.AtomicLong();
                var packets = new java.util.concurrent.atomic.AtomicLong();
                var peak = new java.util.concurrent.atomic.AtomicReference<>(0f);
                var stream = client.record(request, data -> {
                    packets.incrementAndGet();
                    while (data.remaining() >= Float.BYTES) {
                        var v = Math.abs(data.getFloat());
                        samples.incrementAndGet();
                        peak.updateAndGet(p -> Math.max(p, v));
                    }
                });
                System.out.println("recording as source output #" + stream.index());
                Thread.sleep(Long.parseLong(args[2]) * 1000);
                System.out.println("rec " + args[1] + ": " + samples.get() + " samples in " + packets.get() + " packets, peak " + peak.get()
                        + ", open " + stream.isOpen());
                stream.close();
            }
            if (args.length >= 2 && args[0].equals("ops")) {
                for (var i = 1; i < args.length; i++) {
                    var op = args[i].split(":", 2);
                    var ref = DeviceRef.byName(op[1]);
                    switch (op[0]) {
                        case "vol" -> client.setSinkVolume(ref, ChannelVolumes.uniform(client.sink(ref).volume().channels(), 30000));
                        case "mute" -> client.setSinkMute(ref, !client.sink(ref).muted());
                        case "default" -> client.setDefaultSink(op[1]);
                        default -> throw new IllegalArgumentException(op[0]);
                    }
                    System.out.println(System.currentTimeMillis() % 100000 + " done " + args[i]);
                    Thread.sleep(500);
                }
                Thread.sleep(8000);
            }
            if (args.length >= 3 && args[0].equals("bench")) {
                var sink = client.sink(DeviceRef.byName(args[1]));
                var count = Integer.parseInt(args[2]);
                var start = System.nanoTime();
                for (var i = 0; i < count; i++) {
                    client.setSinkVolume(DeviceRef.byIndex(sink.index()), ChannelVolumes.uniform(sink.volume().channels(), 20000 + i * 10L));
                }
                var micros = (System.nanoTime() - start) / 1000 / count;
                System.out.println(count + " volume writes, " + micros + " µs each; now " + client.sink(DeviceRef.byIndex(sink.index())).volume());
                client.setSinkVolume(DeviceRef.byIndex(sink.index()), sink.volume());
            }
        }
    }
}
