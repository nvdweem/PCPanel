package com.getpcpanel.cpp.linux;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
        var pid = getActiveProcessPid(Tool.KDoTool)
                .or(() -> getActiveProcessPid(Tool.XDoTool))
                .orElse(-1);
        if (pid == -1 && !anyActiveWindowToolAvailable()) {
            warnNoActiveWindowTool();
        }
        return pid;
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
     * Resolves the focused window in a single tool invocation and exposes every identifier we can use to match
     * it against a PulseAudio/PipeWire stream:
     * <ul>
     *   <li>the host process name (from {@code ps -o comm});</li>
     *   <li>for Flatpak apps, the sandbox application id (from {@code /proc/<pid>/root/.flatpak-info}), which
     *       equals the stream's {@code pipewire.access.portal.app_id} - sandboxed apps report a sandbox-internal
     *       PID and often no process metadata to PipeWire, so the host process name never matches (see #88);</li>
     *   <li>the window class and the window name (#96). Proton/Wine games are a problem case: their stream is
     *       reported as {@code <game>.exe} but the host process name often does not match it - it may be a wrapper
     *       (gamescope/reaper), in a different PID namespace, or {@code comm}-truncated to 15 chars for long names.
     *       Raw-wine/Lutris games set the window class to the exe name; Steam overwrites it with
     *       {@code steam_app_<id>} but leaves the window name as the game title (e.g. "Deadlock"), which matches
     *       {@code deadlock.exe} once a trailing {@code .exe} is ignored (see {@code SndCtrlPulseAudio.matches}).</li>
     * </ul>
     * The pid/class/name come from one chained call (e.g. {@code getactivewindow getwindowpid getwindowclassname
     * getwindowname}) so a per-knob-tick focus volume change spawns a single helper process, not three.
     */
    public Optional<ActiveWindow> getActiveWindow() {
        // Resolving the focused window spawns a helper process (kdotool/xdotool) plus `ps` - ~40ms total. A
        // single focus-volume knob tick resolves it up to three times (the overlay's show/skip check, the
        // redirector/skip decision, and the actual stream match), and a fast slider sweep fires dozens of
        // ticks; one of those resolutions runs synchronously on the HID input thread, so the per-tick cost
        // throttles event delivery and the volume crawls behind the slider instead of snapping to it. The
        // focused window cannot meaningfully change within such a burst, so cache the resolution for a brief
        // window: every resolution during the burst then reuses a single helper call. The TTL is short enough
        // that a genuine focus change is reflected on the next tick.
        var now = System.nanoTime();
        var cached = activeWindowCache;
        if (cached != null && now - cached.at() < ACTIVE_WINDOW_CACHE_NANOS) {
            return Optional.of(cached.window());
        }
        var result = getActiveWindow(Tool.KDoTool).or(() -> getActiveWindow(Tool.XDoTool));
        if (result.isEmpty() && !anyActiveWindowToolAvailable()) {
            warnNoActiveWindowTool();
        }
        result.ifPresent(window -> activeWindowCache = new CachedActiveWindow(window, now));
        return result;
    }

    private static final long ACTIVE_WINDOW_CACHE_NANOS = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(200);
    private volatile CachedActiveWindow activeWindowCache;

    private record CachedActiveWindow(ActiveWindow window, long at) {
    }

    private Optional<ActiveWindow> getActiveWindow(Tool tool) {
        var command = tool.command();
        if (!tool.available(command)) {
            return Optional.empty();
        }
        List<String> lines;
        try {
            lines = linesFrom(command, "getactivewindow", "getwindowpid", "getwindowclassname", "getwindowname");
        } catch (Exception e) {
            log.error("Unable to resolve active window with {}", tool.tool, e);
            return Optional.empty();
        }
        var pid = NumberUtils.toInt(line(lines, 0), -1);
        if (pid == -1) {
            return Optional.empty();
        }
        return Optional.of(new ActiveWindow(pid, processName(pid), flatpakAppId(pid),
                StringUtils.trimToNull(line(lines, 1)), StringUtils.trimToNull(line(lines, 2))));
    }

    private static @Nullable String line(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : null;
    }

    private @Nullable String processName(int pid) {
        try {
            return lineFrom(hostCmd("ps", "-p", String.valueOf(pid), "-o", "comm="));
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
        var path = "/proc/" + pid + "/root/.flatpak-info";
        try {
            // When PCPanel itself runs inside the Flatpak sandbox the active window's host PID belongs to a
            // different PID namespace, so the file must be read on the host (like pactl/kdotool are). Outside
            // a sandbox just read it directly.
            List<String> lines;
            if (inFlatpakSandbox()) {
                lines = linesFrom(hostCmd("cat", path));
            } else if (Files.isReadable(Path.of(path))) {
                lines = Files.readAllLines(Path.of(path));
            } else {
                return null;
            }
            return parseFlatpakAppId(lines);
        } catch (Exception e) {
            log.debug("Could not read {} for flatpak app id", path, e);
            return null;
        }
    }

    static @Nullable String parseFlatpakAppId(List<String> lines) {
        var inApplication = false;
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                inApplication = "[Application]".equals(trimmed);
            } else if (inApplication && trimmed.startsWith("name=")) {
                return StringUtils.trimToNull(trimmed.substring("name=".length()));
            }
        }
        return null;
    }

    /** Inside the Flatpak sandbox host introspection (ps, /proc) must be forwarded to the host via flatpak-spawn. */
    private static boolean inFlatpakSandbox() {
        return StringUtils.isNotBlank(System.getenv("FLATPAK_ID"));
    }

    private static String[] hostCmd(String... cmd) {
        if (!inFlatpakSandbox()) {
            return cmd;
        }
        var full = new String[cmd.length + 2];
        full[0] = "flatpak-spawn";
        full[1] = "--host";
        System.arraycopy(cmd, 0, full, 2, cmd.length);
        return full;
    }

    private boolean anyActiveWindowToolAvailable() {
        return Tool.KDoTool.available(Tool.KDoTool.command()) || Tool.XDoTool.available(Tool.XDoTool.command());
    }

    private static final long WARN_LOG_INTERVAL_MS = 5L * 60 * 1000;
    private volatile long lastNoToolWarnAt;
    private volatile boolean desktopNotified;

    /**
     * Focus volume and the other focused-window features need an active-window helper. On KDE Plasma
     * (Wayland or X11) that is kdotool, which we now bundle next to the executable in every Linux build,
     * so this should normally never fire - but a user running from source, on an unsupported CPU arch, or
     * with a broken {@code linux.commands.kdotool} override can still end up with nothing available. Make
     * that visible instead of failing silently (the long-standing #88 "the knob just does nothing"
     * complaint): a throttled log line, plus a best-effort desktop notification the first time it happens.
     */
    private void warnNoActiveWindowTool() {
        var now = System.currentTimeMillis();
        if (now - lastNoToolWarnAt > WARN_LOG_INTERVAL_MS) {
            lastNoToolWarnAt = now;
            log.warn("No active-window tool available - focus volume and focused-app features cannot work. "
                    + "Install 'kdotool' (it handles both Wayland and X11 on KDE Plasma; xdotool is not needed "
                    + "alongside it). It ships bundled with the .deb/AppImage/Flatpak, so seeing this usually "
                    + "means a PATH/override issue or an unsupported setup. See linux.md.");
        }
        if (!desktopNotified) {
            desktopNotified = true;
            sendDesktopNotification("PCPanel: focus control unavailable",
                    "Install kdotool to control the focused application's volume on KDE Plasma (Wayland/X11).");
        }
    }

    /** Best-effort desktop popup via notify-send. A missing notify-send is fine - the log line remains the signal. */
    private void sendDesktopNotification(String title, String body) {
        try {
            processHelper.builder(hostCmd("notify-send", "-a", "PCPanel", title, body))
                         .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                         .redirectError(ProcessBuilder.Redirect.DISCARD)
                         .start();
        } catch (Exception e) {
            log.debug("Could not show desktop notification (notify-send missing?)", e);
        }
    }

    /**
     * Resolves a tool bundled next to our own executable (the .deb/AppImage/Flatpak ship kdotool there,
     * beside the companion {@code *.so} libraries), or {@code null} when there is no such sibling - e.g.
     * in dev mode where the running executable is the JVM, not the PCPanel binary.
     */
    private static @Nullable String bundledSibling(String name) {
        return ProcessHandle.current().info().command()
                            .map(Path::of)
                            .map(Path::getParent)
                            .filter(Objects::nonNull)
                            .map(dir -> dir.resolve(name))
                            .filter(Files::isExecutable)
                            .map(Path::toString)
                            .orElse(null);
    }

    private @Nullable String lineFrom(String... cmd) throws IOException {
        var lines = linesFrom(cmd);
        if (lines.isEmpty()) {
            return null;
        }
        return lines.get(0);
    }

    private List<String> linesFrom(String... cmd) throws IOException {
        // Discard stderr so an expected failure (e.g. cat on a non-flatpak target's missing .flatpak-info)
        // doesn't leak to the console.
        var process = processHelper.builder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        return IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
    }

    /**
     * The resolved active window. {@link #identifiers()} returns every string we can match a stream
     * against; {@link #primaryIdentifier()} is the best single name for display / icon lookup,
     * preferring the Flatpak id so sandboxed apps resolve correctly.
     */
    public record ActiveWindow(int pid, @Nullable String process, @Nullable String flatpakAppId, @Nullable String windowClass,
                               @Nullable String windowName) {
        public Set<String> identifiers() {
            return StreamEx.of(process, flatpakAppId, windowClass, windowName).filter(StringUtils::isNotBlank).toSet();
        }

        public @Nullable String primaryIdentifier() {
            return StringUtils.firstNonBlank(flatpakAppId, process, windowClass, windowName);
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

        private String command() {
            var configured = resolveHomeRelativePath(
                    ConfigProvider.getConfig().getOptionalValue("linux.commands." + tool, String.class).orElse(tool));
            // An explicit path override (contains a path separator, e.g. ~/.cargo/bin/kdotool) is honored
            // verbatim. For a bare command name, prefer a copy bundled next to our own executable over the
            // bare PATH lookup, so focus volume works out of the box on KDE without a system-wide install.
            if (configured.indexOf(File.separatorChar) >= 0) {
                return configured;
            }
            return Optional.ofNullable(bundledSibling(configured)).orElse(configured);
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
