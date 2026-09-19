package com.getpcpanel.util.os;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessHelper {
    public ProcessBuilder builder(String... command) {
        var result = new ProcessBuilder(command);
        result.environment().put("LC_ALL", "C");
        return result;
    }

    /**
     * Reads {@code process}'s stdout to the end and waits for it to exit, killing it if it is still running after
     * {@code timeoutMillis}. The output is read while the process runs, since it can exceed a pipe buffer, and the
     * kill ends that read - so a wedged tool (an audio server or D-Bus peer that never answers) can't block the
     * caller past the deadline.
     *
     * @return the output lines, or empty when the process was killed at the deadline (its partial output is
     * not trustworthy); on success the process has exited and {@link Process#exitValue()} is available
     */
    public static Optional<List<String>> readWithDeadline(Process process, long timeoutMillis) throws IOException, InterruptedException {
        var killed = new AtomicBoolean();
        var watchdog = CompletableFuture.runAsync(() -> {
            if (process.isAlive()) {
                killed.set(true);
                process.destroyForcibly();
            }
        }, CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS));
        try {
            List<String> lines;
            try {
                lines = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
            } catch (UncheckedIOException e) {
                if (!killed.get()) {
                    throw e.getCause();
                }
                lines = List.of(); // The kill closed the stream under the read.
            }
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                killed.set(true);
                process.destroyForcibly();
            }
            if (killed.get()) {
                process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                return Optional.empty();
            }
            return Optional.of(lines);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            watchdog.cancel(false);
        }
    }
}
