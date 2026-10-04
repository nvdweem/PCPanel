package com.getpcpanel.alerts.platform.windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.getpcpanel.alerts.WindowTitles;
import com.getpcpanel.integration.program.platform.windows.WinTopLevelWindows;
import com.getpcpanel.platform.WindowsBuild;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The titles of the windows Alt+Tab would list ({@link WinTopLevelWindows}), by the program that owns them, those on
 * other virtual desktops included.
 */
@WindowsBuild
@ApplicationScoped
class WindowsWindowTitles implements WindowTitles {
    @Override
    public Map<String, List<String>> titles() {
        var result = new HashMap<String, List<String>>();
        for (var window : WinTopLevelWindows.list(true)) {
            var title = window.title();
            if (!window.exeStem().isEmpty() && !title.isEmpty()) {
                result.computeIfAbsent(window.exeStem().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(title);
            }
        }
        return result;
    }
}
