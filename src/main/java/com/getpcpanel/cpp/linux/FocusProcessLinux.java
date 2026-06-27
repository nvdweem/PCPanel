package com.getpcpanel.cpp.linux;

import java.util.OptionalInt;

import com.getpcpanel.cpp.FocusProcessService;
import com.getpcpanel.platform.LinuxBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Foreground-window PID via the existing kdotool/xdotool active-window lookup. */
@ApplicationScoped
@LinuxBuild
public class FocusProcessLinux implements FocusProcessService {
    @Inject LinuxProcessHelper processHelper;

    @Override
    public OptionalInt foregroundPid() {
        var pid = processHelper.getActiveProcessPid();
        return pid > 0 ? OptionalInt.of(pid) : OptionalInt.empty();
    }
}
