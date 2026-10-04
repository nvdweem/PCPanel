package com.getpcpanel.util;

import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Static CDI bean accessor for use in non-CDI contexts (e.g., command objects created via Jackson deserialization).
 */
@ApplicationScoped
public class CdiHelper {
    public static <T> T getBean(Class<T> clazz) {
        return CDI.current().select(clazz).get();
    }

    /**
     * Resolves a bean that may be absent in the current platform build (e.g. {@code SndCtrlWindows}
     * is only present on a Windows build), returning empty instead of throwing when unresolvable.
     */
    public static <T> Optional<T> getOptionalBean(Class<T> clazz) {
        var instance = CDI.current().select(clazz);
        if (instance.isResolvable()) {
            return Optional.of(instance.get());
        }
        return Optional.empty();
    }

    /** Fires {@code event} on the CDI event bus from non-CDI code (e.g. a command object). */
    public static void fire(Object event) {
        CDI.current().getBeanManager().getEvent().fire(event);
    }
}
