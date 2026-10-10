package com.getpcpanel.alerts;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/** The desktop notifications apps are showing. One implementation per platform; best-effort, never throws. */
public interface NotificationWatch {
    /** Apps (exe stem or handler id, lower-case) with at least one live notification right now. */
    Set<String> appsWithNotifications();

    /**
     * {@link #appsWithNotifications()}, each with a number that grows with every new notification from that app (an
     * arrival time or a counter), so a new notification counts even while an older one is still listed.
     */
    default Map<String, Long> newestNotifications() {
        return appsWithNotifications().stream().collect(Collectors.toMap(Function.identity(), app -> 0L));
    }

    /** Handler ids seen, for the UI's source picker. */
    default Set<String> sources() {
        return Set.of();
    }

    /** Why notifications can't be followed here, for the settings page; {@code null} when they can. */
    @Nullable
    default String unavailable() {
        return null;
    }
}
