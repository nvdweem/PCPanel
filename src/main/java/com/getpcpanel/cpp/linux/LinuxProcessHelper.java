package com.getpcpanel.cpp.linux;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;

import com.getpcpanel.spring.LinuxImpl;
import com.getpcpanel.util.ProcessHelper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@LinuxImpl
public class LinuxProcessHelper {
    @Inject
    ProcessHelper processHelper;

    @PostConstruct
    void logResolvedTools() {
        for (var tool : Tool.values()) {
            var command = tool.command();
            log.info("Active window tool {} command: {} (available: {})", tool.tool, command, tool.available(command));
        }
    }

    public ProcessBuilder builder(String... command) {
        return processHelper.builder(command);
    }

    public int getActiveProcessPid() {
        return getActiveProcessPid(Tool.KDoTool)
                .or(() -> getActiveProcessPid(Tool.XDoTool))
                .orElse(-1);
    }

    private Optional<Integer> getActiveProcessPid(Tool tool) {
        var command = tool.command();
        if (tool.available(command)) {
            try {
                var line = lineFrom(command, "getactivewindow", "getwindowpid");
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
        return lines.get(0);
    }

    @Getter
    private enum Tool {
        XDoTool("xdotool"),
        KDoTool("kdotool");

        private final String tool;

        Tool(String tool) {
            this.tool = tool;
        }

        @PostConstruct
        public void bla() {
            log.error("Post construct");
        }

        private String command() {
            var configured = ConfigProvider.getConfig().getOptionalValue("linux.commands." + tool, String.class).orElse(tool);
            return resolveHomeRelativePath(configured);
        }

        private boolean available(String command) {
            var available = ProcessConditionalHelper.isProcessAvailable(command);
            log.debug("Active Window tool {} command {} enabled: {}", tool, command, available);
            return available;
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
