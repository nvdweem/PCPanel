package com.getpcpanel.alerts;

import java.util.Set;

/** Which apps are using a microphone right now. One implementation per platform; best-effort, never throws. */
public interface MicUsage {
    /** Lower-case executable file names (or app ids) of the apps using a microphone now. */
    Set<String> appsUsingMic();
}
