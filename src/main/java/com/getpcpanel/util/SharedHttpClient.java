package com.getpcpanel.util;

import java.net.http.HttpClient;

/**
 * A single, lazily-created {@link HttpClient} shared across the app.
 *
 * <p>Each {@code HttpClient.newHttpClient()} spins up its own selector-manager thread and worker
 * pool; the app previously created three (WaveLink, OBS, the version check). Sharing one keeps the
 * thread count down. None of the callers close the client, so a process-wide singleton is safe.
 */
public final class SharedHttpClient {
    private static volatile HttpClient instance;

    private SharedHttpClient() {
    }

    public static HttpClient get() {
        var local = instance;
        if (local == null) {
            synchronized (SharedHttpClient.class) {
                local = instance;
                if (local == null) {
                    local = HttpClient.newHttpClient();
                    instance = local;
                }
            }
        }
        return local;
    }
}
