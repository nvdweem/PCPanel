package com.getpcpanel.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.util.FileUtil;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import lombok.extern.log4j.Log4j2;

/**
 * Log access: the recurring pain point was that a failing endpoint surfaced only as an HTTP 500 and
 * diagnosing it meant locating and grepping {@code ${pcpanel.root}/logs/logging.log}. These tools
 * give an agent the log tail (filtered) and the most recent exception with its stack trace directly.
 */
@Log4j2
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class LogTools {
    private static final int MAX_LIMIT = 500;
    private static final int MAX_CHARS = 60_000;

    @Inject FileUtil fileUtil;
    @Inject McpLogBuffer logBuffer;

    @Tool(description = "Tail the app log file (${pcpanel.root}/logs/logging.log), newest lines last. "
            + "Optional level (e.g. ERROR, WARN, INFO) keeps only lines at/with that token; contains "
            + "filters by substring; limit caps the number of returned lines (default 100, max 500).")
    public RecentLogs pcpanel_recent_logs(
            @ToolArg(required = false, description = "Only lines containing this level token, e.g. ERROR") String level,
            @ToolArg(required = false, description = "Max lines to return (default 100, max 500)") Integer limit,
            @ToolArg(required = false, description = "Only lines containing this substring") String contains) {
        var cap = limit == null ? 100 : Math.min(Math.max(1, limit), MAX_LIMIT);
        var file = fileUtil.getFile("logs/logging.log").toPath();
        if (!Files.isReadable(file)) {
            return new RecentLogs(false, file.toString(),
                    "Log file not readable (file logging may be disabled). Use pcpanel_last_error for "
                            + "the in-memory error buffer.", List.of());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new RecentLogs(false, file.toString(), describe(e), List.of());
        }
        var filtered = new ArrayList<String>();
        for (var line : lines) {
            if (level != null && !StringUtils.containsIgnoreCase(line, level)) {
                continue;
            }
            if (contains != null && !StringUtils.contains(line, contains)) {
                continue;
            }
            filtered.add(line);
        }
        var from = Math.max(0, filtered.size() - cap);
        var tail = new ArrayList<>(filtered.subList(from, filtered.size()));
        capChars(tail);
        return new RecentLogs(true, file.toString(), null, tail);
    }

    @Tool(description = "The most recent ERROR / exception with its full stack trace, from an in-memory "
            + "ring buffer (works even when file logging is off). Returns found=false if nothing has "
            + "errored since startup.")
    public LastError pcpanel_last_error() {
        var entry = logBuffer.lastError();
        if (entry == null) {
            return new LastError(false, null);
        }
        return new LastError(true, entry);
    }

    /** Drop oldest lines until the joined payload is under the char cap. */
    private static void capChars(List<String> lines) {
        var total = 0;
        for (var l : lines) {
            total += l.length() + 1;
        }
        while (total > MAX_CHARS && !lines.isEmpty()) {
            total -= lines.removeFirst().length() + 1;
        }
    }

    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    public record RecentLogs(boolean available, String path, String note, List<String> lines) {
    }

    public record LastError(boolean found, McpLogBuffer.Entry error) {
    }
}
