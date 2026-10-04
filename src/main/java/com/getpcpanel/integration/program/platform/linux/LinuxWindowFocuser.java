package com.getpcpanel.integration.program.platform.linux;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.platform.process.LinuxProcessHelper;
import com.getpcpanel.platform.process.LinuxProcessHelper.WindowTool;
import com.getpcpanel.util.ExeNames;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Finds an app's windows by window class with the desktop's window tool ({@link LinuxProcessHelper#windowTool()}).
 * xdotool and kdotool share their commands: {@code search --class}, {@code getactivewindow}, {@code windowactivate} and
 * {@code windowminimize}. Hyprland focuses with {@code hyprctl dispatch focuswindow} and has no minimised state, so
 * there an app in front stays in front.
 *
 * <p>A window whose class is the app's name, ignoring case, is preferred ({@code steam} must not raise
 * {@code steam_app_570}). Only when there is none does a class that contains the name count, because some apps name
 * their class differently from their process ({@code chrome} runs as class {@code Google-chrome}).
 */
@Log4j2
@Unremovable
@LinuxBuild
@ApplicationScoped
class LinuxWindowFocuser implements WindowFocuser {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** POSIX extended and RE2 metacharacters, escaped so a name matches itself. */
    private static final Pattern REGEX_META = Pattern.compile("[\\\\.\\[\\](){}*+?^$|]");

    @Inject ProcessHelper processes;
    @Inject LinuxProcessHelper linuxProcesses;

    @Override
    public Result focusOrMinimize(String exe, boolean minimizeIfFocused) {
        return focusOrMinimize(linuxProcesses.windowTool(), exe, minimizeIfFocused);
    }

    Result focusOrMinimize(WindowTool tool, String exe, boolean minimizeIfFocused) {
        var stem = ExeNames.stem(exe);
        if (stem.isEmpty()) {
            return Result.NOT_RUNNING;
        }
        if (tool.kind() == WindowTool.Kind.HYPRCTL) {
            var className = literal(stem);
            return hyprlandFocus(tool, "(?i)^" + className + "$") || hyprlandFocus(tool, "(?i)" + className) ? Result.FOCUSED : Result.NOT_RUNNING;
        }
        // xdotool reads POSIX regexes and kdotool JavaScript ones; letter classes ignore case in both.
        var className = caseless(stem);
        var windows = search(tool, "^" + className + "$");
        if (windows.isEmpty()) {
            windows = search(tool, className);
        }
        if (windows.isEmpty()) {
            return Result.NOT_RUNNING;
        }
        if (minimizeIfFocused) {
            var active = run(tool.command(), "getactivewindow");
            var activeWindow = active == null || active.stdout().isEmpty() ? null : active.stdout().getFirst().strip();
            if (windows.contains(activeWindow) && succeeded(run(tool.command(), "windowminimize", activeWindow))) {
                return Result.MINIMIZED;
            }
        }
        for (var window : windows) {
            if (succeeded(run(tool.command(), "windowactivate", window))) {
                return Result.FOCUSED;
            }
        }
        return Result.NOT_RUNNING;
    }

    private boolean hyprlandFocus(WindowTool tool, String classRegex) {
        var answer = run(tool.command(), "dispatch", "focuswindow", "class:" + classRegex);
        return answer != null && answer.stdout().contains("ok");
    }

    private List<String> search(WindowTool tool, String classRegex) {
        var search = run(tool.command(), "search", "--class", classRegex);
        return search == null ? List.of() : search.stdout().stream().map(String::strip).filter(StringUtils::isNotEmpty).toList();
    }

    /** {@code name} as a regex that matches it literally, each letter in both cases. */
    static String caseless(String name) {
        var out = new StringBuilder();
        name.codePoints().forEach(c -> {
            var lower = Character.toLowerCase(c);
            var upper = Character.toUpperCase(c);
            if (lower != upper) {
                out.append('[').appendCodePoint(lower).appendCodePoint(upper).append(']');
            } else {
                out.append(literal(Character.toString(c)));
            }
        });
        return out.toString();
    }

    private static String literal(String name) {
        return REGEX_META.matcher(name).replaceAll(m -> Matcher.quoteReplacement("\\" + m.group()));
    }

    private static boolean succeeded(@Nullable ProcessHelper.Result result) {
        return result != null && result.succeeded();
    }

    private @Nullable ProcessHelper.Result run(String... command) {
        try {
            return processes.run(TIMEOUT, command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("{} failed: {}", command[0], e.toString());
            return null;
        }
    }
}
