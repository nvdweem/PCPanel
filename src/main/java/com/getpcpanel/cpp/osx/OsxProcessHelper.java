package com.getpcpanel.cpp.osx;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.cpp.IProcessHelper;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.ProcessHelper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Determines the frontmost and running applications using the built-in lsappinfo tool (no permissions required).
 */
@Log4j2
@ApplicationScoped
@MacBuild
@RequiredArgsConstructor
public class OsxProcessHelper implements IProcessHelper {
    private static final Pattern KEY_VALUE = Pattern.compile("\"(\\w+)\"\\s*=\\s*\"?(.*?)\"?\\s*$");
    private static final Pattern APP_HEADER = Pattern.compile("^\\s*\\d+\\)\\s+\"(.*)\"\\s+ASN:.*");
    private static final Pattern EXECUTABLE_PATH = Pattern.compile("executable path=\"(.*)\"");
    private static final Pattern APP_TYPE = Pattern.compile("\\btype=\"(\\w+)\"");
    private static final Pattern APP_PID = Pattern.compile("\\bpid\\s*=\\s*(\\d+)");
    private final ProcessHelper processHelper;

    public record FrontmostApp(int pid, String name, String executablePath) {
    }

    public record RunningApp(int pid, String name, String executablePath, String type) {
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
            var info = keyValuesFrom("lsappinfo", "info", "-only", "name,pid,executablepath", asn.trim());
            var pid = NumberUtils.toInt(info.get("pid"), -1);
            var name = info.getOrDefault("LSDisplayName", "");
            var executable = info.getOrDefault("CFBundleExecutablePath", "");
            if (pid == -1 && StringUtils.isBlank(executable)) {
                return null;
            }
            return new FrontmostApp(pid, name, executable);
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
            var pid = -1;
            for (var line : IOUtils.readLines(processHelper.builder("lsappinfo", "list").start().getInputStream(), Charset.defaultCharset())) {
                var header = APP_HEADER.matcher(line);
                if (header.matches()) {
                    addApp(result, pid, name, executable, type);
                    name = header.group(1);
                    executable = null;
                    type = null;
                    pid = -1;
                    continue;
                }
                executable = firstGroup(EXECUTABLE_PATH, line, executable);
                type = firstGroup(APP_TYPE, line, type);
                var pidMatch = firstGroup(APP_PID, line, pid == -1 ? null : String.valueOf(pid));
                pid = NumberUtils.toInt(pidMatch, -1);
            }
            addApp(result, pid, name, executable, type);
            return result;
        } catch (Exception e) {
            log.error("Unable to list applications", e);
            return List.of();
        }
    }

    private static void addApp(List<RunningApp> result, int pid, @Nullable String name, @Nullable String executable, @Nullable String type) {
        if (name != null && StringUtils.isNotBlank(executable)) {
            result.add(new RunningApp(pid, name, executable, StringUtils.defaultString(type)));
        }
    }

    private static @Nullable String firstGroup(Pattern pattern, String line, @Nullable String current) {
        if (current != null) {
            return current;
        }
        var matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Map<String, String> keyValuesFrom(String... cmd) throws IOException {
        var result = new HashMap<String, String>();
        for (var line : IOUtils.readLines(processHelper.builder(cmd).start().getInputStream(), Charset.defaultCharset())) {
            var matcher = KEY_VALUE.matcher(line.trim());
            if (matcher.matches()) {
                result.put(matcher.group(1), matcher.group(2));
            }
        }
        return result;
    }

    private @Nullable String lineFrom(String... cmd) throws IOException {
        var lines = IOUtils.readLines(processHelper.builder(cmd).start().getInputStream(), Charset.defaultCharset());
        return lines.isEmpty() ? null : lines.getFirst();
    }
}
