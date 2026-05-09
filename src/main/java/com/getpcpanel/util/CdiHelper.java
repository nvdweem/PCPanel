package com.getpcpanel.util;

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
}
