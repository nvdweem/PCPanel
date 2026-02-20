package com.getpcpanel.cpp.linux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ProcessConditionalHelper {
    private static final Map<String, Boolean> resultCache = new ConcurrentHashMap<>();

    public static boolean isProcessAvailable(String process) {
        return resultCache.computeIfAbsent(process, k -> {
            try {
                return new ProcessBuilder("which", k)
                        .start()
                        .waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
