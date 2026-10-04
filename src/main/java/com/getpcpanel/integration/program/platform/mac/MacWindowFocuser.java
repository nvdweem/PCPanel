package com.getpcpanel.integration.program.platform.mac;

import com.getpcpanel.integration.program.WindowFocuser;
import com.getpcpanel.platform.MacBuild;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;

/** Reports every app as not running, so the action opens it: {@code open} brings a running app to the front itself. */
@Unremovable
@MacBuild
@ApplicationScoped
class MacWindowFocuser implements WindowFocuser {
    @Override
    public Result focusOrMinimize(String exe, boolean minimizeIfFocused) {
        return Result.NOT_RUNNING;
    }
}
