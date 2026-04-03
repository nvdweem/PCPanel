package com.getpcpanel.util;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

/**
 * Static accessor for CDI events from non-CDI contexts (e.g., plain Thread subclasses).
 */
@ApplicationScoped
public class AppEvents {
    @SuppressWarnings("StaticNonFinalField")
    private static AppEvents instance;

    @Inject
    Event<Object> eventBus;

    @PostConstruct
    void init() {
        instance = this;
    }

    public static void fire(Object event) {
        if (instance != null) {
            instance.eventBus.fire(event);
        }
    }
}
