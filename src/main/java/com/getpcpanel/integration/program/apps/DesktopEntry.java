package com.getpcpanel.integration.program.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/** Reads a freedesktop {@code .desktop} file as an {@link InstalledApp}. */
public final class DesktopEntry {
    private static final String MAIN_GROUP = "[Desktop Entry]";

    private DesktopEntry() {
    }

    /**
     * The app {@code lines} (the file at {@code path}) describe, or null for an entry a launcher does not show:
     * {@code NoDisplay}, {@code Hidden}, not an application, or without a name or command.
     */
    public static @Nullable InstalledApp parse(String path, List<String> lines) {
        var keys = new HashMap<String, String>();
        var inMain = false;
        for (var line : lines) {
            var trimmed = line.strip();
            if (trimmed.startsWith("[")) {
                inMain = MAIN_GROUP.equals(trimmed);
            } else if (inMain && !trimmed.startsWith("#") && trimmed.contains("=")) {
                keys.putIfAbsent(StringUtils.substringBefore(trimmed, "=").strip(), StringUtils.substringAfter(trimmed, "=").strip());
            }
        }
        var name = keys.get("Name");
        var exec = keys.get("Exec");
        if (StringUtils.isAnyBlank(name, exec)
                || !"Application".equals(keys.getOrDefault("Type", "Application"))
                || "true".equals(keys.get("NoDisplay"))
                || "true".equals(keys.get("Hidden"))) {
            return null;
        }
        var windowClass = StringUtils.defaultIfBlank(keys.get("StartupWMClass"), windowClass(exec));
        return StringUtils.isBlank(windowClass) ? null : new InstalledApp(name, path, windowClass);
    }

    /**
     * The window class an {@code Exec} line's program most likely has: its file name, past an {@code env VAR=value}
     * prefix; for {@code flatpak run}, the {@code --command} or else the app id.
     */
    static @Nullable String windowClass(String exec) {
        var args = arguments(exec);
        var i = 0;
        if (i < args.size() && "env".equals(fileName(args.get(i)))) {
            i++;
            while (i < args.size() && args.get(i).contains("=")) {
                i++;
            }
        }
        if (i >= args.size()) {
            return null;
        }
        var program = fileName(args.get(i));
        if ("flatpak".equals(program) && i + 1 < args.size() && "run".equals(args.get(i + 1))) {
            for (var arg : args.subList(i + 2, args.size())) {
                if (arg.startsWith("--command=")) {
                    return fileName(StringUtils.substringAfter(arg, "="));
                }
            }
            return args.subList(i + 2, args.size()).stream().filter(a -> !a.startsWith("-")).findFirst().orElse(null);
        }
        return program;
    }

    /** {@code exec} split at unquoted whitespace; a double-quoted argument may hold spaces and backslash escapes. */
    private static List<String> arguments(String exec) {
        var args = new ArrayList<String>();
        var current = new StringBuilder();
        var quoted = false;
        var inArgument = false;
        for (var i = 0; i < exec.length(); i++) {
            var c = exec.charAt(i);
            if (quoted && c == '\\' && i + 1 < exec.length()) {
                current.append(exec.charAt(++i));
            } else if (c == '"') {
                quoted = !quoted;
                inArgument = true;
            } else if (!quoted && Character.isWhitespace(c)) {
                if (inArgument) {
                    args.add(current.toString());
                    current.setLength(0);
                    inArgument = false;
                }
            } else {
                current.append(c);
                inArgument = true;
            }
        }
        if (inArgument) {
            args.add(current.toString());
        }
        return args;
    }

    private static String fileName(String path) {
        return StringUtils.substringAfterLast("/" + path, "/");
    }
}
