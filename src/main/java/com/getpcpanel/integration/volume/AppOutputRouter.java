package com.getpcpanel.integration.volume;

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nullable;

import com.getpcpanel.util.ExeNames;

/**
 * Sends apps to an output device: every audio stream of the named apps plays on that device from then on. One
 * build-time implementation per platform (Windows, Linux), reached from the command layer via
 * {@link com.getpcpanel.util.CdiHelper}. Best-effort: a failure is logged, never thrown.
 */
public interface AppOutputRouter {
    /**
     * @param exeNames the apps' executable names, compared without path, case or a trailing {@code .exe}
     * @param deviceId the output device's id; null sends the apps back to the default output
     * @return display name of the device the apps now use, or null when nothing matched
     */
    @Nullable
    String route(Set<String> exeNames, @Nullable String deviceId);

    /** Whether one of {@code names} names the app {@code actual} (a path or a name): no path, no case, no {@code .exe}. */
    static boolean namesApp(Collection<String> names, @Nullable String actual) {
        return names.stream().anyMatch(n -> ExeNames.sameApp(actual, n));
    }
}
