package com.getpcpanel.cpp.linux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

public abstract class ProcessConditionalHelper {
    private static final Map<String, Boolean> resultCache = new ConcurrentHashMap<>();

    public static boolean isProcessAvailable(String process) {
        var normalizedProcess = StringUtils.trimToNull(process);
        if (normalizedProcess == null) {
            return false;
        }

        return resultCache.computeIfAbsent(normalizedProcess, k -> checkFileExists(k) || checkWhichProcess(k));
    }

    private static boolean checkFileExists(String pathStr) {
        try {
            var path = Path.of(pathStr);
            return Files.exists(path) && Files.isExecutable(path);
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean checkWhichProcess(String k) {
        try {
            return new ProcessBuilder("which", k)
                    .start()
                    .waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
