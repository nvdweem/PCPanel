package com.getpcpanel.util.os;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * The one place the application starts external processes. Each entry point fixes how the process is treated,
 * so a caller can't forget a deadline or leave a pipe unread:
 * <ul>
 *     <li>{@link #run} / {@link #runWithInput} wait for the process, at most until a deadline, and return its
 *     output. A process still running at the deadline is killed.</li>
 *     <li>{@link #launch} starts a process that is meant to outlive the call (a program started from a button, a
 *     file manager, an installer) and walks away. Its output is discarded, so it can't block on a full pipe.</li>
 *     <li>{@link #stream} follows a long-running process line by line until it exits.</li>
 * </ul>
 * The environment is inherited unchanged unless the caller passes variables to set. Commands whose output is
 * parsed pass {@link #PARSEABLE_OUTPUT}; anything started on the user's behalf keeps the user's own settings.
 */
@Log4j2
@ApplicationScoped
public class ProcessHelper {
    /** For commands whose output is parsed: fixes labels and number formats to the untranslated C locale. */
    public static final Map<String, String> PARSEABLE_OUTPUT = Map.of("LC_ALL", "C");

    /**
     * After a process exits its output is already in the pipes, so the readers finish almost at once. A process it
     * left behind (xclip and wl-copy leave a clipboard server holding the inherited pipes) keeps them from ever
     * seeing the end, so the readers only get this long before the output read so far is taken.
     */
    private static final Duration OUTPUT_GRACE = Duration.ofMillis(250);
    /** How long a killed process gets to disappear before the call returns anyway. */
    private static final Duration KILL_GRACE = Duration.ofSeconds(1);

    /**
     * The outcome of a process that was waited on. {@link #exitCode} is only meaningful when the process did not
     * time out; {@link #stdout} and {@link #stderr} hold what it wrote either way, one entry per line.
     */
    public record Result(boolean timedOut, int exitCode, List<String> stdout, List<String> stderr) {
        /** Exited within the deadline with status 0. */
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    public Result run(Duration timeout, String... command) throws IOException, InterruptedException {
        return run(timeout, Map.of(), command);
    }

    public Result run(Duration timeout, Map<String, String> env, String... command) throws IOException, InterruptedException {
        return execute(timeout, env, null, command);
    }

    /** Like {@link #run(Duration, String...)}, with {@code input} written to the process's stdin. */
    public Result runWithInput(Duration timeout, byte[] input, String... command) throws IOException, InterruptedException {
        return execute(timeout, Map.of(), input, command);
    }

    public void launch(String... command) throws IOException {
        launch(null, command);
    }

    /** Starts {@code command} in {@code directory} without waiting for it. */
    public void launch(@Nullable File directory, String... command) throws IOException {
        var process = builder(command)
                .directory(directory)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        process.getOutputStream().close();
    }

    public int stream(Consumer<String> onLine, String... command) throws IOException, InterruptedException {
        return stream(Map.of(), onLine, command);
    }

    /**
     * Starts {@code command} and hands every line it writes (stdout and stderr together, so a process that dies at
     * once says why) to {@code onLine}, until the process ends. Returns its exit code.
     */
    public int stream(Map<String, String> env, Consumer<String> onLine, String... command) throws IOException, InterruptedException {
        var builder = builder(command).redirectErrorStream(true);
        builder.environment().putAll(env);
        var process = builder.start();
        try {
            process.getOutputStream().close();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                //noinspection NestedAssignment
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                }
            }
            if (!process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                kill(process); // Closed its output but kept running.
            }
            return process.isAlive() ? -1 : process.exitValue();
        } finally {
            if (process.isAlive()) {
                kill(process);
            }
        }
    }

    /**
     * Splits a command line into arguments exactly like {@link Runtime#exec(String)} does, for free-form commands
     * a user typed, so they are run as they always were.
     */
    public static String[] splitCommandLine(String commandLine) {
        var tokenizer = new StringTokenizer(commandLine);
        var parts = new String[tokenizer.countTokens()];
        for (var i = 0; i < parts.length; i++) {
            parts[i] = tokenizer.nextToken();
        }
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty command");
        }
        return parts;
    }

    /** Creates the builder for {@code command}; every entry point starts its process from here. */
    protected ProcessBuilder builder(String... command) {
        return new ProcessBuilder(command);
    }

    private Result execute(Duration timeout, Map<String, String> env, @Nullable byte[] input, String... command) throws IOException, InterruptedException {
        var builder = builder(command);
        builder.environment().putAll(env);
        var process = builder.start();
        var stdout = new LineReader(process.getInputStream(), command[0] + " stdout");
        var stderr = new LineReader(process.getErrorStream(), command[0] + " stderr");
        if (input == null) {
            process.getOutputStream().close();
        } else {
            // Written on its own thread: a process that doesn't read its input would otherwise block us once the
            // pipe fills, before the deadline is even being watched.
            var writer = new Thread(() -> writeInput(process, input), command[0] + " stdin");
            writer.setDaemon(true);
            writer.start();
        }
        try {
            var exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                log.debug("{} did not finish within {}ms; killing it", command[0], timeout.toMillis());
                kill(process);
            }
            return new Result(!exited, exited ? process.exitValue() : -1, stdout.lines(), stderr.lines());
        } catch (InterruptedException e) {
            kill(process);
            throw e;
        }
    }

    private static void writeInput(Process process, byte[] input) {
        try (var stdin = process.getOutputStream()) {
            stdin.write(input);
        } catch (IOException e) {
            log.debug("Could not write the input of process {}", process.pid(), e);
        }
    }

    private static void kill(Process process) throws InterruptedException {
        // Descendants first: once the process is gone they are no longer reachable through it.
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Reads a process's output stream on its own thread, so it keeps flowing whatever the caller is doing. */
    private static final class LineReader {
        private final List<String> lines = new ArrayList<>();
        private final Thread thread;

        LineReader(InputStream stream, String name) {
            thread = new Thread(() -> readAll(stream), name);
            thread.setDaemon(true);
            thread.start();
        }

        private void readAll(InputStream stream) {
            try (var reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()))) {
                String line;
                //noinspection NestedAssignment
                while ((line = reader.readLine()) != null) {
                    synchronized (lines) {
                        lines.add(line);
                    }
                }
            } catch (IOException e) {
                log.trace("Stopped reading {}", thread.getName(), e); // The process was killed under the read.
            }
        }

        /** Everything read once the stream ended, or within {@link #OUTPUT_GRACE} if something still holds it open. */
        List<String> lines() throws InterruptedException {
            thread.join(OUTPUT_GRACE.toMillis());
            synchronized (lines) {
                return List.copyOf(lines);
            }
        }
    }
}
