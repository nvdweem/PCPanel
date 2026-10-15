package com.getpcpanel.platform.process;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.platform.IProcessHelper;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Determines the frontmost and running applications using the built-in lsappinfo tool (no permissions required).
 */
@Log4j2
@ApplicationScoped
@Unremovable // resolved via CdiHelper.getBean(IProcessHelper) for screen share — keep even if no @Inject point remains
@MacBuild
@RequiredArgsConstructor
public class OsxProcessHelper implements IProcessHelper {
    private static final Pattern KEY_VALUE = Pattern.compile("\"(\\w+)\"\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))");
    private static final Pattern INFO_HEADER = Pattern.compile("^\\s*\"(.*)\"\\s+ASN:.*");
    private static final Pattern APP_HEADER = Pattern.compile("^\\s*\\d+\\)\\s+\"(.*)\"\\s+ASN:.*");
    private static final Pattern BUNDLE_ID = Pattern.compile("(?i)bundleid=\"([^\"]*)\"");
    private static final Pattern EXECUTABLE_PATH = Pattern.compile("executable path=\"(.*)\"");
    private static final Pattern APP_TYPE = Pattern.compile("\\btype=\"(\\w+)\"");
    private static final Pattern APP_PID = Pattern.compile("\\bpid\\s*=\\s*(\\d+)");
    private static final Pattern COALITION_PIDS = Pattern.compile("coalition:\\s*\\d+\\s*\\{\\s*([^}]+)\\s*\\}");
    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(3);
    private final ProcessHelper processHelper;

    public record FrontmostApp(int pid, String name, String executablePath, @Nullable String bundleId) {
        public FrontmostApp(int pid, String name, String executablePath) {
            this(pid, name, executablePath, null);
        }
    }

    public record RunningApp(int pid, String name, String executablePath, String type, @Nullable String bundleId, Set<Integer> coalitionPids) {
        public RunningApp(int pid, String name, String executablePath, String type, Set<Integer> coalitionPids) {
            this(pid, name, executablePath, type, null, coalitionPids);
        }

        public RunningApp(int pid, String name, String executablePath, String type) {
            this(pid, name, executablePath, type, null, Set.of());
        }

        /** Regular GUI applications (Dock/app switcher), as opposed to UIElement agents and BackgroundOnly daemons. */
        public boolean foreground() {
            return "Foreground".equals(type);
        }
    }

    @Override
    public OptionalInt foregroundPid() {
        var app = getFrontmostApp();
        return app != null && app.pid() > 0 ? OptionalInt.of(app.pid()) : OptionalInt.empty();
    }

    public @Nullable FrontmostApp getFrontmostApp() {
        try {
            var asn = lineFrom("lsappinfo", "front");
            if (StringUtils.isBlank(asn)) {
                return null;
            }
            var app = parseFrontmostApp(output("lsappinfo", "info", "-only", "name,pid,executablepath,bundleid", asn.trim()));
            if (app == null) {
                log.debug("lsappinfo gave no pid or executable for the frontmost application {}", asn.trim());
            }
            return app;
        } catch (Exception e) {
            log.error("Unable to determine frontmost application", e);
            return null;
        }
    }

    /**
     * Lists the applications known to the launch services database. Returns an empty list when lsappinfo fails.
     */
    public List<RunningApp> listApps() {
        try {
            var result = new ArrayList<RunningApp>();
            String name = null;
            String executable = null;
            String type = null;
            String bundleId = null;
            var pid = -1;
            Set<Integer> coalitionPids = Set.of();
            for (var line : output("lsappinfo", "list")) {
                var header = APP_HEADER.matcher(line);
                if (header.matches()) {
                    addApp(result, pid, name, executable, type, bundleId, coalitionPids);
                    name = header.group(1);
                    executable = null;
                    type = null;
                    bundleId = null;
                    pid = -1;
                    coalitionPids = Set.of();
                    continue;
                }
                bundleId = firstGroup(BUNDLE_ID, line, bundleId);
                executable = firstGroup(EXECUTABLE_PATH, line, executable);
                type = firstGroup(APP_TYPE, line, type);
                var pidMatch = firstGroup(APP_PID, line, pid == -1 ? null : String.valueOf(pid));
                pid = NumberUtils.toInt(pidMatch, -1);
                if (coalitionPids.isEmpty()) {
                    var coalitionMatch = firstGroup(COALITION_PIDS, line, null);
                    if (coalitionMatch != null) {
                        coalitionPids = parsePids(coalitionMatch);
                    }
                }
            }
            addApp(result, pid, name, executable, type, bundleId, coalitionPids);
            return result;
        } catch (Exception e) {
            log.error("Unable to list applications", e);
            return List.of();
        }
    }

    private static void addApp(List<RunningApp> result, int pid, @Nullable String name, @Nullable String executable, @Nullable String type, @Nullable String bundleId, Set<Integer> coalitionPids) {
        if (name != null && StringUtils.isNotBlank(executable)) {
            result.add(new RunningApp(pid, name, executable, StringUtils.defaultString(type), bundleId, coalitionPids));
        }
    }

    private static Set<Integer> parsePids(String pidList) {
        var set = new LinkedHashSet<Integer>();
        for (var token : pidList.trim().split("\\s+")) {
            var p = NumberUtils.toInt(token, -1);
            if (p > 0) {
                set.add(p);
            }
        }
        return Set.copyOf(set);
    }

    private static @Nullable String firstGroup(Pattern pattern, String line, @Nullable String current) {
        if (current != null) {
            return current;
        }
        var matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Reads {@code lsappinfo info} output. Depending on the macOS version it answers {@code -only} with
     * {@code "key"="value"} pairs or with its full human-readable block ({@code "Name" ASN:...} header,
     * {@code executable path="..."}, {@code pid = 123 ...}), so both are understood.
     */
    static @Nullable FrontmostApp parseFrontmostApp(List<String> lines) {
        var info = new HashMap<String, String>();
        String headerName = null;
        String executable = null;
        String pid = null;
        String bundleId = null;
        for (var line : lines) {
            var matcher = KEY_VALUE.matcher(line);
            while (matcher.find()) {
                info.putIfAbsent(matcher.group(1), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            }
            var header = INFO_HEADER.matcher(line);
            if (headerName == null && header.matches()) {
                headerName = header.group(1);
            }
            bundleId = firstGroup(BUNDLE_ID, line, bundleId);
            executable = firstGroup(EXECUTABLE_PATH, line, executable);
            pid = firstGroup(APP_PID, line, pid);
        }
        var pidValue = NumberUtils.toInt(StringUtils.firstNonBlank(info.get("pid"), pid), -1);
        var name = StringUtils.firstNonBlank(info.get("LSDisplayName"), headerName, "");
        var executablePath = StringUtils.firstNonBlank(info.get("CFBundleExecutablePath"), executable, "");
        var bundleIdValue = StringUtils.firstNonBlank(info.get("bundleID"), info.get("bundleid"), info.get("CFBundleIdentifier"), bundleId);
        if (pidValue == -1 && StringUtils.isBlank(executablePath)) {
            return null;
        }
        return new FrontmostApp(pidValue, name, executablePath, bundleIdValue);
    }

    private @Nullable String lineFrom(String... cmd) throws IOException {
        var lines = output(cmd);
        return lines.isEmpty() ? null : lines.getFirst();
    }

    /**
     * The stdout of {@code cmd}. Focus volume asks for the frontmost app on the command thread, so a wedged
     * lsappinfo is given up on at the deadline instead of stalling every knob.
     */
    private List<String> output(String... cmd) throws IOException {
        try {
            var result = processHelper.run(TOOL_TIMEOUT, ProcessHelper.PARSEABLE_OUTPUT, cmd);
            if (result.timedOut()) {
                throw new IOException(cmd[0] + " did not finish within " + TOOL_TIMEOUT.toMillis() + "ms");
            }
            return result.stdout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(cmd[0] + " was interrupted", e);
        }
    }
}
