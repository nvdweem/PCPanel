package com.getpcpanel.cpp.linux;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.ConditionalOnLinux;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@ConditionalOnLinux
@RequiredArgsConstructor
public class PulseAudioWrapper {
    public static final int NO_OP_IDX = -1;
    public static final int DEFAULT_DEVICE = -2;
    private static final Pattern pactlFirstLine = Pattern.compile("(.*) #(\\d+)");

    public List<PulseAudioTarget> getDevices() {
        return StreamEx.of(execAndParse(InOutput.output)).append(execAndParse(InOutput.input)).toList();
    }

    public void setDeviceVolume(boolean output, int idx, float volume) {
        if (idx == NO_OP_IDX) {
            return;
        }
        var target = output ? "set-sink-volume" : "set-source-volume";
        //noinspection NumericCastThatLosesPrecision
        pactl(target, idxOrDefaultDevice(idx), (int) (volume * 100) + "%");
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
        //noinspection NumericCastThatLosesPrecision
        pactl("set-sink-input-volume", String.valueOf(index), (int) (volume * 100) + "%");
    }

    public void muteSession(int index, MuteType mute) {
        pactl("set-sink-input-mute", String.valueOf(index), muteTypeToMute(mute));
    }

    public List<PulseAudioTarget> execAndParse(InOutput type) {
        var ret = new ArrayList<PulseAudioTarget>();
        var cmdOutput = runAndRead(new ProcessBuilder("pactl", "list", type.pulseType));

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

                var parts = line.split(":");
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

    @SneakyThrows
    private synchronized void pactl(String... cmd) {
        var fullCmd = new String[cmd.length + 1];
        fullCmd[0] = "pactl";
        System.arraycopy(cmd, 0, fullCmd, 1, cmd.length);
        log.debug("Executing: {}", String.join(" ", fullCmd));
        var process = new ProcessBuilder(fullCmd).start();

        if (log.isTraceEnabled()) {
            var lines = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
            log.trace("Response: \n{}", String.join("\n", lines));
        }
    }

    @SneakyThrows
    private List<String> runAndRead(ProcessBuilder pb) {
        var process = pb.start();
        return IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
    }

    private String idxOrDefaultDevice(int idx) {
        return idx == DEFAULT_DEVICE ? "@DEFAULT_SINK@" : String.valueOf(idx);
    }

    @Builder
    record PulseAudioTarget(int index, boolean isDefault, Map<String, String> metas, Map<String, String> properties, InOutput type) {
    }

    @RequiredArgsConstructor
    enum InOutput {
        input("sources"), output("sinks"), session("sink-inputs");

        private final String pulseType;
    }

    private String muteTypeToMute(MuteType type) {
        return switch (type) {
            case mute -> "1";
            case unmute -> "0";
            case toggle -> "toggle";
        };
    }
}
