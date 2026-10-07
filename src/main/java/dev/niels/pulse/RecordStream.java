package dev.niels.pulse;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import lombok.extern.log4j.Log4j2;

/**
 * A recording on a {@link PulseClient} connection. Its samples arrive on the client's reader thread, so the consumer
 * must hand them on quickly. It ends when {@link #close()}d, when the server ends it (its source went away) or with the
 * connection; {@link #ended()} completes then.
 */
@Log4j2
public final class RecordStream implements Closeable {
    private final PulseClient client;
    private final int channel;
    private final int index;
    private final Consumer<ByteBuffer> samples;
    private final CompletableFuture<Void> ended = new CompletableFuture<>();

    RecordStream(PulseClient client, int channel, int index, Consumer<ByteBuffer> samples) {
        this.client = client;
        this.channel = channel;
        this.index = index;
        this.samples = samples;
    }

    /** The source output's index, as {@code pactl list source-outputs} shows it. */
    public int index() {
        return index;
    }

    int channel() {
        return channel;
    }

    public boolean isOpen() {
        return !ended.isDone();
    }

    public CompletableFuture<Void> ended() {
        return ended;
    }

    void deliver(ByteBuffer data) {
        if (ended.isDone()) {
            return;
        }
        try {
            samples.accept(data);
        } catch (RuntimeException e) {
            log.warn("Recording consumer failed", e);
        }
    }

    void end() {
        ended.complete(null);
    }

    @Override
    public void close() {
        if (ended.isDone()) {
            return;
        }
        end();
        client.deleteRecordStream(this);
    }
}
