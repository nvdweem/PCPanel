package com.getpcpanel.cpp.osx;

import java.util.OptionalInt;

import com.getpcpanel.cpp.FocusProcessService;
import com.getpcpanel.platform.MacBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Foreground-window PID via the frontmost application reported by {@code lsappinfo}. */
@ApplicationScoped
@MacBuild
public class FocusProcessOsx implements FocusProcessService {
    @Inject OsxProcessHelper processHelper;

    @Override
    public OptionalInt foregroundPid() {
        var app = processHelper.getFrontmostApp();
        return app != null && app.pid() > 0 ? OptionalInt.of(app.pid()) : OptionalInt.empty();
    }
}
