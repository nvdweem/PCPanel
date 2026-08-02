package com.getpcpanel.platform.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.annotation.Nullable;

import com.getpcpanel.platform.IProcessHelper;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.arc.Unremovable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@Unremovable // resolved via CdiHelper.getBean(IProcessHelper) for screen share — keep even if no @Inject point remains
@LinuxBuild
public class LinuxProcessHelper implements IProcessHelper {
    @Inject
    ProcessHelper processHelper;

    @Override
    public OptionalInt foregroundPid() {
        var pid = getActiveProcessPid();
        return pid > 0 ? OptionalInt.of(pid) : OptionalInt.empty();
    }

    @PostConstruct
    void logResolvedTools() {
        for (var tool : Tool.values()) {
            var command = tool.command();
            log.info("Active window tool {} command: {} (present: {})", tool.tool, command, tool.available(command));
        }
    }

    public ProcessBuilder builder(String... command) {
        return processHelper.builder(command);
    }

    public int getActiveProcessPid() {
        return getActiveWindow().map(ActiveWindow::pid).orElse(-1);
    }

    public @Nullable String getActiveProcess() {
        return getActiveWindow().map(ActiveWindow::process).orElse(null);
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
        var result = resolveActiveWindow();
        result.ifPresent(window -> activeWindowCache = new CachedActiveWindow(window, now));
        return result;
    }

    /** One resolution attempt across every tool, bypassing the burst cache. Also refreshes {@link #toolStatus}. */
    private Optional<ActiveWindow> resolveActiveWindow() {
        var result = getActiveWindow(Tool.KDoTool).or(() -> getActiveWindow(Tool.XDoTool));
        if (result.isEmpty()) {
            warnFocusUnavailable();
        }
        return result;
    }

    private static final long ACTIVE_WINDOW_CACHE_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
    private volatile CachedActiveWindow activeWindowCache;

    private record CachedActiveWindow(ActiveWindow window, long at) {
    }

    // xdotool only learned `getwindowclassname` in 2021 (jordansissel/xdotool#247); older builds - e.g. the
    // 3.20160805 that still ships on Linux Mint/Cinnamon - abort the whole chained invocation on the unknown
    // subcommand and print nothing, so the focused window never resolves and focus volume silently does nothing
    // (#112). kdotool always supports it. Probe optimistically and, if a tool turns out not to, remember it so
    // every later tick skips the doomed command and only loses the (fallback-only) window class - PID matching,
    // the robust path, is unaffected.
    private final Map<Tool, Boolean> windowClassSupported = new ConcurrentHashMap<>();

    /**
     * Why each tool did or didn't resolve the focused window on its last attempt. A tool being installed says
     * nothing about whether it works here - inside the Flatpak both "tools" always exist (kdotool is bundled,
     * xdotool is a {@code flatpak-spawn --host} shim), so presence alone can never explain a silent failure.
     * This is what {@link #focusDiagnostics()} reports and what the throttled warning quotes (#151).
     */
    private final Map<Tool, String> toolStatus = new ConcurrentHashMap<>();

    private Optional<ActiveWindow> getActiveWindow(Tool tool) {
        var command = tool.command();
        if (!tool.available(command)) {
            toolStatus.put(tool, "not installed (" + command + ")");
            return Optional.empty();
        }
        var withClass = windowClassSupported.getOrDefault(tool, Boolean.TRUE);
        var window = queryActiveWindow(tool, command, withClass);
        if (window.isEmpty() && withClass) {
            // The chain may have failed because this tool lacks getwindowclassname. Retry without it; if that
            // resolves the window, the classname subcommand was the culprit - stop sending it from now on.
            var fallback = queryActiveWindow(tool, command, false);
            fallback.ifPresent(w -> {
                windowClassSupported.put(tool, Boolean.FALSE);
                log.info("{} does not support 'getwindowclassname' (pre-2021); omitting it from focus matching - "
                        + "PID-based matching is unaffected.", tool.tool);
            });
            return fallback;
        }
        return window;
    }

    private Optional<ActiveWindow> queryActiveWindow(Tool tool, String command, boolean withClass) {
        CommandOutput output;
        try {
            output = run(StreamEx.of(command).append(windowQuerySubcommands(withClass)).toArray(String[]::new));
        } catch (Exception e) {
            toolStatus.put(tool, "could not be started: " + e);
            log.error("Unable to resolve active window with {}", tool.tool, e);
            return Optional.empty();
        }
        var pid = NumberUtils.toInt(line(output.stdout(), 0), -1);
        if (pid == -1) {
            // The tool ran but told us nothing. Its stderr is the only thing that says why (no KWin on a
            // non-KDE desktop, no X11 display on Wayland, a missing host binary behind the Flatpak shim),
            // so keep it rather than discarding it into a silent empty Optional.
            toolStatus.put(tool, command + ": " + output.failureDetail());
            return Optional.empty();
        }
        toolStatus.put(tool, "resolved the focused window (pid " + pid + ")");
        var windowClass = withClass ? StringUtils.trimToNull(line(output.stdout(), 1)) : null;
        var windowName = StringUtils.trimToNull(line(output.stdout(), withClass ? 2 : 1));
        return Optional.of(new ActiveWindow(pid, processName(pid), flatpakAppId(pid), windowClass, windowName));
    }

    /**
     * The chained active-window subcommands, in output order. {@code getwindowclassname} (a #96 fallback
     * identifier for Wine/Steam games) is dropped when a tool can't parse it, so the universally supported
     * pid+name still resolve - see {@link #getActiveWindow(Tool)}.
     */
    static String[] windowQuerySubcommands(boolean withClass) {
        return withClass
                ? new String[] { "getactivewindow", "getwindowpid", "getwindowclassname", "getwindowname" }
                : new String[] { "getactivewindow", "getwindowpid", "getwindowname" };
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
                lines = run(hostCmd("cat", path)).stdout();
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

    private static final long WARN_LOG_INTERVAL_MS = 5L * 60 * 1000;
    private volatile long lastNoToolWarnAt;
    private volatile boolean desktopNotified;

    /**
     * Focus volume and the other focused-window features need a helper that can name the focused window.
     * Only two desktops can be served: KDE Plasma (kdotool, over KWin's D-Bus scripting API, on both Wayland
     * and X11) and any X11 session (xdotool). On a non-KDE Wayland session - GNOME above all - there is no
     * API to use: GNOME's {@code org.gnome.Shell.Introspect} is allow-listed to the xdg-desktop-portal
     * implementations, and wlroots exposes nothing equivalent. So this is a supported-configuration problem
     * far more often than an installation problem.
     *
     * <p>Reaching that conclusion used to be impossible from the outside, because the failure was silent in
     * exactly the case that matters: the old check only complained when <em>no tool was installed</em>, and
     * inside the Flatpak both are always installed (kdotool is bundled, xdotool is a {@code flatpak-spawn}
     * shim) - so a GNOME-Wayland user got no log line, no notification and a bare "Could not read the focused
     * app" in the UI (#151). Warn on the resolution failing instead, and quote what each tool actually said.
     */
    private void warnFocusUnavailable() {
        var now = System.currentTimeMillis();
        if (now - lastNoToolWarnAt > WARN_LOG_INTERVAL_MS) {
            lastNoToolWarnAt = now;
            log.warn("Could not resolve the focused window - focus volume and the focused-app features cannot work. "
                    + "{}. Tools: {}. Focused-window detection needs KDE Plasma (kdotool, bundled with the "
                    + ".deb/AppImage/Flatpak) or an X11 session (xdotool); a non-KDE Wayland session such as GNOME "
                    + "exposes no API for it. See linux.md.", describeSession(), describeTools());
        }
        if (!desktopNotified) {
            desktopNotified = true;
            sendDesktopNotification("PCPanel: focused-app control unavailable",
                    "The focused window could not be resolved on this desktop. Focus volume needs KDE Plasma or an "
                            + "X11 session; see the log for details.");
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
     * The session facts that decide whether focused-window detection can work at all, plus what each tool
     * last reported. A reporter can't be expected to know any of this, and asking for it costs a round trip
     * per issue - so the bug-report bundle carries it (see {@code SystemInfoCollector}). The tools are probed
     * live, so the answer is current even when the user never triggered the feature.
     */
    @Override
    public Map<String, String> focusDiagnostics() {
        var out = new LinkedHashMap<String, String>();
        out.put("desktop", env("XDG_CURRENT_DESKTOP"));
        out.put("session type", env("XDG_SESSION_TYPE"));
        out.put("wayland display", env("WAYLAND_DISPLAY"));
        out.put("x11 display", env("DISPLAY"));
        out.put("flatpak sandbox", inFlatpakSandbox() ? System.getenv("FLATPAK_ID") : "no");

        var window = resolveActiveWindow();
        for (var tool : Tool.values()) {
            out.put(tool.tool + " command", tool.command());
            out.put(tool.tool + " result", toolStatus.getOrDefault(tool, "not tried"));
        }
        out.put("focused window", window.map(ActiveWindow::describe).orElse("could not be resolved"));
        return out;
    }

    /**
     * The actionable half of {@link #focusDiagnostics()}. Which of the three cases applies is decided by the
     * session, because that is what decides whether anything <em>can</em> work:
     * <ul>
     *   <li>KDE Plasma — kdotool is the supported path and is bundled, so a failure here is a real fault and
     *       what kdotool printed is the useful part;</li>
     *   <li>an X11 session on any other desktop — xdotool is the supported path, and it is not bundled, so
     *       "install it" is usually the answer;</li>
     *   <li>a non-KDE Wayland session — nothing can work, and saying so is kinder than an error that implies
     *       the user misconfigured something (#151).</li>
     * </ul>
     */
    @Override
    public Optional<String> focusUnavailableReason() {
        if (getActiveWindow().isPresent()) {
            return Optional.empty();
        }
        return Optional.of(focusUnavailableReason(env("XDG_CURRENT_DESKTOP"), System.getenv("DISPLAY"),
                toolDetail(Tool.KDoTool), toolDetail(Tool.XDoTool)));
    }

    static String focusUnavailableReason(String desktop, @Nullable String x11Display, String kdotoolDetail, String xdotoolDetail) {
        if (StringUtils.containsIgnoreCase(desktop, "KDE")) {
            return "kdotool could not read the focused window from KWin (" + kdotoolDetail + ").";
        }
        if (StringUtils.isNotBlank(x11Display)) {
            return "xdotool could not read the focused window on this X11 session (" + xdotoolDetail
                    + "). Installing xdotool usually fixes this.";
        }
        return "This is a " + desktop + " Wayland session. Only KDE Plasma (via kdotool) and X11 sessions "
                + "(via xdotool) let an application read the focused window, so the focused-app features cannot work here.";
    }

    private String toolDetail(Tool tool) {
        return toolStatus.getOrDefault(tool, "not tried");
    }

    /** One-line summary of why detection may be impossible here, for the warning that a reporter will quote. */
    private static String describeSession() {
        return "desktop=" + env("XDG_CURRENT_DESKTOP") + ", session=" + env("XDG_SESSION_TYPE")
                + ", wayland=" + env("WAYLAND_DISPLAY") + ", x11=" + env("DISPLAY")
                + (inFlatpakSandbox() ? ", flatpak=" + System.getenv("FLATPAK_ID") : "");
    }

    private String describeTools() {
        return StreamEx.of(Tool.values())
                       .map(tool -> tool.tool + " -> " + toolStatus.getOrDefault(tool, "not tried"))
                       .joining("; ");
    }

    private static String env(String name) {
        return StringUtils.defaultIfBlank(System.getenv(name), "unset");
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
        var lines = run(cmd).stdout();
        return lines.isEmpty() ? null : lines.get(0);
    }

    /** How long a helper gets before it is killed, so a wedged tool can't stall the HID input thread forever. */
    private static final long COMMAND_TIMEOUT_MS = 3000;
    private static final int MAX_STDERR_CHARS = 400;
    private static final int EXIT_TIMED_OUT = -1;
    private static final int EXIT_INTERRUPTED = -2;

    /**
     * Runs a helper and keeps everything needed to explain a failure: stdout, the exit code, and stderr.
     * stderr used to be discarded, which is precisely why a non-working tool looked identical to a working
     * one that found no window.
     */
    private CommandOutput run(String... cmd) throws IOException {
        var process = processHelper.builder(cmd).start();
        // Drain stderr on its own thread: a helper writing more than the pipe buffer would otherwise
        // deadlock against our stdout read.
        var stderr = new AtomicReference<>("");
        var drain = new Thread(() -> {
            try (var err = process.getErrorStream()) {
                stderr.set(StringUtils.abbreviate(StringUtils.trimToEmpty(
                        String.join(" ", IOUtils.readLines(err, Charset.defaultCharset()))), MAX_STDERR_CHARS));
            } catch (IOException e) {
                log.debug("Could not read stderr of {}", cmd[0], e);
            }
        }, "focus-tool-stderr");
        drain.setDaemon(true);
        drain.start();

        var stdout = IOUtils.readLines(process.getInputStream(), Charset.defaultCharset());
        int exitCode;
        try {
            if (process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
            } else {
                process.destroyForcibly();
                exitCode = EXIT_TIMED_OUT;
            }
            drain.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            exitCode = EXIT_INTERRUPTED;
        }
        return new CommandOutput(exitCode, stdout, stderr.get());
    }

    /** A helper invocation's full outcome — stdout is the answer, the rest is why there wasn't one. */
    record CommandOutput(int exitCode, List<String> stdout, String stderr) {
        /** Why this invocation produced nothing usable, phrased for a log line or a bug report. */
        String failureDetail() {
            var reason = switch (exitCode) {
                case EXIT_TIMED_OUT -> "timed out after " + COMMAND_TIMEOUT_MS + "ms";
                case EXIT_INTERRUPTED -> "interrupted";
                case 0 -> "no window reported";
                default -> "exit " + exitCode;
            };
            return StringUtils.isBlank(stderr) ? reason : reason + " - " + stderr;
        }
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

        /** Everything we know about the window, on one line, for diagnostics. */
        public String describe() {
            return "pid " + pid + " " + identifiers();
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

        /**
         * Whether the command exists. Note that this says nothing about whether it can do its job here:
         * inside the Flatpak kdotool is bundled and xdotool is a host-spawn shim, so both are always
         * "available" even on a desktop where neither can resolve anything - see {@link #toolStatus}.
         */
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
