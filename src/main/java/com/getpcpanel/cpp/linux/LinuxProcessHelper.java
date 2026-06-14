package com.getpcpanel.cpp.linux;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.ProcessHelper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@LinuxBuild
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
            return processName(pid);
        } catch (Exception e) {
            log.error("Unable to run process", e);
        }
        return null;
    }

    /**
     * Resolves the active window once and exposes every identifier we can use to match it against a
     * PulseAudio/PipeWire stream: the host process name (from {@code ps}) and, for Flatpak apps, the
     * sandbox application id (from {@code /proc/<pid>/root/.flatpak-info}). The Flatpak id is needed
     * because sandboxed apps report a sandbox-internal PID and often no process metadata to PipeWire,
     * so the host process name never matches their stream - but the Flatpak id equals the stream's
     * {@code pipewire.access.portal.app_id} (see #88).
     */
    public Optional<ActiveWindow> getActiveWindow() {
        var pid = getActiveProcessPid();
        if (pid == -1) {
            return Optional.empty();
        }
        return Optional.of(new ActiveWindow(pid, processName(pid), flatpakAppId(pid)));
    }

    private @Nullable String processName(int pid) {
        try {
            return lineFrom("ps", "-p", String.valueOf(pid), "-o", "comm=");
        } catch (Exception e) {
            log.error("Unable to resolve process name for pid {}", pid, e);
            return null;
        }
    }

    /**
     * Reads the Flatpak application id of a host process, if it is a Flatpak. Each Flatpak instance
     * has a {@code .flatpak-info} ini file at the root of its sandbox; from the host it is reachable
     * at {@code /proc/<hostpid>/root/.flatpak-info}, with the app id under {@code [Application] name=}.
     */
    private @Nullable String flatpakAppId(int pid) {
        var info = Path.of("/proc", String.valueOf(pid), "root", ".flatpak-info");
        if (!Files.isReadable(info)) {
            return null;
        }
        try {
            var inApplication = false;
            for (var line : Files.readAllLines(info)) {
                var trimmed = line.trim();
                if (trimmed.startsWith("[")) {
                    inApplication = "[Application]".equals(trimmed);
                } else if (inApplication && trimmed.startsWith("name=")) {
                    return StringUtils.trimToNull(trimmed.substring("name=".length()));
                }
            }
        } catch (Exception e) {
            log.debug("Could not read {} for flatpak app id", info, e);
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

    /**
     * The resolved active window. {@link #identifiers()} returns every string we can match a stream
     * against; {@link #primaryIdentifier()} is the best single name for display / icon lookup,
     * preferring the Flatpak id so sandboxed apps resolve correctly.
     */
    public record ActiveWindow(int pid, @Nullable String process, @Nullable String flatpakAppId) {
        public Set<String> identifiers() {
            return StreamEx.of(process, flatpakAppId).filter(StringUtils::isNotBlank).toSet();
        }

        public @Nullable String primaryIdentifier() {
            return StringUtils.firstNonBlank(flatpakAppId, process);
        }
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
