package com.getpcpanel.cpp.linux;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import jakarta.inject.Inject;
import com.getpcpanel.spring.LinuxImpl;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.util.ProcessHelper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@LinuxImpl
public class LinuxProcessHelper {
    @Inject
    ProcessHelper processHelper;

    public ProcessBuilder builder(String... command) {
        return processHelper.builder(command);
    }

    public int getActiveProcessPid() {
        return getActiveProcessPid(Tool.KDoTool)
                .or(() -> getActiveProcessPid(Tool.XDoTool))
                .orElse(-1);
    }

    private Optional<Integer> getActiveProcessPid(Tool tool) {
        if (tool.available()) {
            try {
                var line = lineFrom(tool.command, "getactivewindow", "getwindowpid");
                return Optional.of(NumberUtils.toInt(line, -1)).filter(v -> v != -1);
            } catch (Exception e) {
                log.error("Unable to run process", e);
            }
        }
        return Optional.empty();
    }

    public @Nullable String getActiveProcess() {
        try {
            var pid = getActiveProcessPid();
            if (pid == -1)
                return null;
            return lineFrom("ps", "-p", String.valueOf(pid), "-o", "comm=");
        } catch (Exception e) {
            log.error("Unable to run process", e);
        }
        return null;
    }

    private @Nullable String lineFrom(String... cmd) throws IOException {
        var lines = IOUtils.readLines(processHelper.builder(cmd).start().getInputStream(), Charset.defaultCharset());
        if (lines.isEmpty()) {
            return null;
        }
        return lines.getFirst();
    }

    @Getter
    private enum Tool {
        XDoTool("xdotool"),
        KDoTool("kdotool");

        private final String command;
        private final boolean available;

        Tool(String tool) {
            command = resolveHomeRelativePath(Objects.requireNonNullElse(MainFX.getContext().getEnvironment().getProperty("linux.commands." + tool), tool));
            available = ProcessConditionalHelper.isProcessAvailable(command);
            log.info("Active Window tool {} enabled: {}", tool, available);
        }

        private static String resolveHomeRelativePath(String process) {
            var userHome = System.getProperty("user.home");
            if ("~".equals(process)) {
                return userHome;
            }
            if (process.startsWith("~/")) {
                return userHome + process.substring(1);
            }
            return process;
        }
    }
}
