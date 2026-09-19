package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@LinuxBuild
class PulseAudioWrapper {
    public static final int NO_OP_IDX = -1;
    public static final int DEFAULT_DEVICE = -2;
    private static final Pattern pactlFirstLine = Pattern.compile("(.*) #(\\d+)");
    @Inject
    ProcessHelper processHelper;
    long timeoutMillis = 2_000;

    public static int volumeFtoI(float volume) {
        return Math.round(volume * 65536);
    }

    public static float volumeItoF(int volume) {
        return volume / 65536f;
    }

    /** Sinks and sources, with the server's default sink and default source flagged. */
    public List<PulseAudioTarget> devices() {
        var defaults = defaultDeviceNames();
        return StreamEx.of(execAndParse(InOutput.output))
                       .append(execAndParse(InOutput.input))
                       .map(t -> t.toBuilder().isDefault(t.name() != null && t.name().equals(defaults.get(t.type()))).build())
                       .toList();
    }

    /** The names of the default sink ({@link InOutput#output}) and default source ({@link InOutput#input}). */
    Map<InOutput, String> defaultDeviceNames() {
        return parseDefaultDeviceNames(runAndRead("pactl", "info"));
    }

    /**
     * Reads {@code Default Sink:} and {@code Default Source:} from {@code pactl info}, which every pactl version
     * prints ({@code get-default-sink} only exists since PulseAudio 15).
     */
    static Map<InOutput, String> parseDefaultDeviceNames(List<String> info) {
        var names = new EnumMap<InOutput, String>(InOutput.class);
        for (var line : info) {
            var parts = line.split(":", 2);
            if (parts.length < 2) {
                continue;
            }
            var value = parts[1].trim();
            switch (parts[0].trim()) {
                case "Default Sink" -> names.put(InOutput.output, value);
                case "Default Source" -> names.put(InOutput.input, value);
                default -> {
                }
            }
        }
        return names;
    }

    public void setDeviceVolume(boolean output, int idx, float volume) {
        if (idx == NO_OP_IDX) {
            return;
        }
        var target = output ? "set-sink-volume" : "set-source-volume";
        pactl(target, idxOrDefaultDevice(idx), String.valueOf(volumeFtoI(volume)));
    }

    public void muteDevice(boolean output, int idx, MuteType type) {
        if (idx == NO_OP_IDX) {
            return;
        }
        var target = output ? "set-sink-mute" : "set-source-mute";
        pactl(target, idxOrDefaultDevice(idx), muteTypeToMute(type));
    }

    public void setDefaultDevice(boolean output, int index) {
        var target = output ? "set-default-sink" : "set-default-source";
        pactl(target, String.valueOf(index));
    }

    public List<PulseAudioTarget> getSessions() {
        return execAndParse(InOutput.session);
    }

    public void setSessionVolume(int index, float volume) {
        pactl("set-sink-input-volume", String.valueOf(index), String.valueOf(volumeFtoI(volume)));
    }

    public void muteSession(int index, MuteType mute) {
        pactl("set-sink-input-mute", String.valueOf(index), muteTypeToMute(mute));
    }

    List<PulseAudioTarget> execAndParse(InOutput type) {
        var ret = new ArrayList<PulseAudioTarget>();
        var cmdOutput = runAndRead("pactl", "list", type.pulseType);

        PulseAudioTarget.PulseAudioTargetBuilder paTarget = null;
        var properties = new HashMap<String, String>();
        var metas = new HashMap<String, String>();

        var readingProperties = false;
        for (var fullLine : cmdOutput) {
            var line = StringUtils.trimToEmpty(fullLine);

            var firstLineMatcher = pactlFirstLine.matcher(line);
            if (firstLineMatcher.find()) {
                if (paTarget != null) {
                    ret.add(paTarget.properties(properties).metas(metas).build());
                }
                paTarget = PulseAudioTarget.builder().index(NumberUtils.toInt(firstLineMatcher.group(2), -1)).type(type);
                properties = new HashMap<>();
                metas = new HashMap<>();
                readingProperties = false;
                continue;
            }
            if (paTarget == null)
                continue;

            if (readingProperties && line.contains("=")) {
                var parts = line.split("=");
                properties.put(StringUtils.trimToEmpty(parts[0]), StringUtils.strip(StringUtils.trimToEmpty(parts[1]), "\""));
            } else if (!readingProperties && line.contains(":")) {
                if (StringUtils.equals(line, "Properties:")) {
                    readingProperties = true;
                    continue;
                }

                var parts = line.split(":", 2);
                if (parts.length > 1) {
                    metas.put(StringUtils.trimToEmpty(parts[0]), StringUtils.trimToEmpty(parts[1]));
                }
            }
        }
        if (paTarget != null) {
            ret.add(paTarget.properties(properties).metas(metas).type(type).build());
        }

        return ret;
    }

    /**
     * Runs a write and returns once pactl has finished. Together with {@code synchronized} this keeps exactly one
     * write in flight, so successive values land in the order they were sent - the command thread coalesces knob
     * values that arrive meanwhile, so only the newest one is applied next.
     */
    private synchronized void pactl(String... cmd) {
        var fullCmd = new String[cmd.length + 1];
        fullCmd[0] = "pactl";
        System.arraycopy(cmd, 0, fullCmd, 1, cmd.length);
        log.debug("Executing: {}", String.join(" ", fullCmd));
        try {
            var result = run(fullCmd);
            log.trace("Response: \n{}", String.join("\n", result.stdout()));
        } catch (PactlTimeoutException e) {
            log.warn("{}; the change was not applied", e.getMessage());
        } catch (IOException e) {
            // A missing or non-functional pactl (no PulseAudio/PipeWire - e.g. a headless box or a CI
            // runner) must not abort the volume operation. Degrade to a no-op; the read paths return
            // empty so nothing was targeted anyway.
            onPactlUnavailable(e);
        }
    }

    /**
     * @throws PactlTimeoutException when pactl does not finish in time, so the caller can keep what it already
     *                               knows instead of treating a stalled audio server as "no devices"
     */
    private List<String> runAndRead(String... command) {
        try {
            return run(command).stdout();
        } catch (IOException e) {
            // pactl missing/unrunnable: report no devices/sessions rather than crashing. On Linux audio
            // control is best-effort, so its absence degrades to a no-op ISndCtrl - the app still starts
            // and serves the UI (a system without PulseAudio/PipeWire, or CI without pactl installed).
            onPactlUnavailable(e);
            return List.of();
        }
    }

    /** Runs {@code command} to completion within {@link #timeoutMillis}; a failed run is logged with what pactl said. */
    private ProcessHelper.Result run(String... command) throws IOException {
        try {
            var result = processHelper.run(Duration.ofMillis(timeoutMillis), ProcessHelper.PARSEABLE_OUTPUT, command);
            if (result.timedOut()) {
                throw new PactlTimeoutException(String.join(" ", command) + " did not finish within " + timeoutMillis + "ms");
            }
            if (result.exitCode() != 0) {
                log.debug("{} exited with {}: {}", String.join(" ", command), result.exitCode(), String.join("\n", result.stderr()));
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PactlTimeoutException(String.join(" ", command) + " was interrupted");
        }
    }

    private volatile boolean pactlUnavailableLogged;

    /** Warn once that pactl is unavailable (so a per-event write path can't spam the log), then debug. */
    private void onPactlUnavailable(IOException e) {
        if (!pactlUnavailableLogged) {
            pactlUnavailableLogged = true;
            log.warn("pactl (PulseAudio/PipeWire) is unavailable - Linux audio control is disabled. "
                    + "Install pulseaudio-utils (it provides pactl) to control volume: {}", e.getMessage());
        } else {
            log.debug("pactl invocation failed", e);
        }
    }

    private String idxOrDefaultDevice(int idx) {
        return idx == DEFAULT_DEVICE ? "@DEFAULT_SINK@" : String.valueOf(idx);
    }

    @Nonnull
    List<String> getDebugOutput() {
        return StreamEx.of(InOutput.values())
                       .map(t -> new String[] { "pactl", "list", t.pulseType })
                       .mapToEntry(this::debugRun)
                       .mapKeys(cmd -> String.join(" ", cmd))
                       .mapValues(lines -> String.join("\n", lines))
                       .mapKeyValue((cmd, lns) -> cmd + ":\n" + lns)
                       .toList();
    }

    /** Everything pactl wrote, errors included, for the debug dump. */
    private List<String> debugRun(String[] cmd) {
        try {
            var result = run(cmd);
            return StreamEx.of(result.stdout()).append(result.stderr()).toList();
        } catch (PactlTimeoutException e) {
            return List.of(e.getMessage());
        } catch (IOException e) {
            onPactlUnavailable(e);
            return List.of();
        }
    }

    static final class PactlTimeoutException extends RuntimeException {
        PactlTimeoutException(String message) {
            super(message);
        }
    }

    @Builder(toBuilder = true)
    public record PulseAudioTarget(int index, boolean isDefault, Map<String, String> metas, Map<String, String> properties, InOutput type) {
        /** The device's name ({@code Name:}), which is how {@code pactl info} refers to the default device. */
        @Nullable
        String name() {
            return metas == null ? null : metas.get("Name");
        }
    }

        enum InOutput {
        input("sources"), output("sinks"), session("sink-inputs");

        private final String pulseType;

        InOutput(String pulseType) {
            this.pulseType = pulseType;
        }
    }

    private String muteTypeToMute(MuteType type) {
        return switch (type) {
            case mute -> "1";
            case unmute -> "0";
            case toggle -> "toggle";
        };
    }
}
