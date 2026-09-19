package com.getpcpanel.util.os;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stand-in for an external tool ({@code pactl}, {@code kdotool}, ...), launched as a real subprocess so process
 * handling (waiting, deadlines, output draining) is exercised the way it runs against the real tool. Modes:
 * <ul>
 *     <li>{@code hang} - prints a line, then never exits (a tool stuck on an unresponsive audio server or D-Bus)</li>
 *     <li>{@code slow <millis> <markerFile>} - sleeps, then creates the marker file and exits</li>
 *     <li>{@code sinks <count>} - prints {@code count} sink entries in {@code pactl list} format and exits</li>
 *     <li>{@code fail <code> <message>} - prints {@code message} to stderr and exits with {@code code}</li>
 *     <li>{@code echo-env <name>} - prints the value of environment variable {@code name}, or {@code <unset>}</li>
 *     <li>{@code env-to-file <name> <file>} - writes that value to {@code file} (for callers that discard output)</li>
 *     <li>{@code cat} - copies stdin to stdout</li>
 *     <li>{@code daemonize} - starts a {@code hang} that inherits this process's stdout and stderr, prints its
 *     pid, and exits 0 - the way {@code xclip} and {@code wl-copy} leave a clipboard server holding the pipes</li>
 * </ul>
 */
public final class FakeProcess {
    private FakeProcess() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "hang" -> {
                System.out.println("Sink #0");
                System.out.flush();
                Thread.sleep(Long.MAX_VALUE);
            }
            case "slow" -> {
                Thread.sleep(Long.parseLong(args[1]));
                Files.createFile(Path.of(args[2]));
            }
            case "sinks" -> {
                var count = Integer.parseInt(args[1]);
                var out = new StringBuilder();
                for (var i = 0; i < count; i++) {
                    out.append("Sink #").append(i).append('\n')
                       .append("\tName: sink-").append(i).append('\n')
                       .append("\tDescription: A sink with a reasonably long description to pad the output ").append(i).append('\n');
                }
                System.out.print(out);
            }
            case "fail" -> {
                System.err.println(args[2]);
                System.exit(Integer.parseInt(args[1]));
            }
            case "echo-env" -> System.out.println(envValue(args[1]));
            case "env-to-file" -> Files.writeString(Path.of(args[2]), envValue(args[1]));
            case "cat" -> System.out.write(System.in.readAllBytes());
            case "daemonize" -> {
                var server = new ProcessBuilder(command("hang")).inheritIO().start();
                System.out.println(server.pid());
            }
            default -> throw new IllegalArgumentException("unknown mode " + args[0]);
        }
        System.out.flush();
    }

    private static String envValue(String name) {
        return Objects.requireNonNullElse(System.getenv(name), "<unset>");
    }

    /** A command line that runs this class in a fresh JVM with {@code args}. */
    public static String[] command(String... args) {
        var command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-Dfile.encoding=" + StandardCharsets.UTF_8.name(),
                "-cp", System.getProperty("java.class.path"),
                FakeProcess.class.getName()));
        command.addAll(List.of(args));
        return command.toArray(String[]::new);
    }
}
