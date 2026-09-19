package com.getpcpanel.platform.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.util.os.ProcessHelper;

public abstract class ProcessConditionalHelper {
    private static final Duration WHICH_TIMEOUT = Duration.ofSeconds(5);
    private static final Map<String, Boolean> resultCache = new ConcurrentHashMap<>();

    public static boolean isProcessAvailable(ProcessHelper processes, String process) {
        var normalizedProcess = StringUtils.trimToNull(process);
        if (normalizedProcess == null) {
            return false;
        }

        return resultCache.computeIfAbsent(normalizedProcess, k -> checkFileExists(k) || checkWhichProcess(processes, k));
    }

    private static boolean checkFileExists(String pathStr) {
        try {
            var path = Path.of(pathStr);
            return Files.exists(path) && Files.isExecutable(path);
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean checkWhichProcess(ProcessHelper processes, String k) {
        try {
            return processes.run(WHICH_TIMEOUT, "which", k).succeeded();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
