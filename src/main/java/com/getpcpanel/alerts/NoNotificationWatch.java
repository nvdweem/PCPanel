package com.getpcpanel.alerts;

import java.util.Set;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
class NoNotificationWatch implements NotificationWatch {
    @Override
    public Set<String> appsWithNotifications() {
        return Set.of();
    }
}
