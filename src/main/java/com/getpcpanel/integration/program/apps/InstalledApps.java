package com.getpcpanel.integration.program.apps;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class InstalledApps {
    private InstalledApps() {
    }

    /**
     * {@code apps} sorted by name, ignoring case, keeping the first of each name and executable: the same app is often
     * listed both for the user and for all users.
     */
    public static List<InstalledApp> tidy(List<InstalledApp> apps) {
        var unique = new LinkedHashMap<String, InstalledApp>();
        for (var app : apps) {
            unique.putIfAbsent(app.name().toLowerCase(Locale.ROOT) + '\0' + app.exe().toLowerCase(Locale.ROOT), app);
        }
        return unique.values().stream().sorted(Comparator.comparing(InstalledApp::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
