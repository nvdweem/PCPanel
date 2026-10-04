package com.getpcpanel.alerts;

import java.util.List;

/** The notification lights showing changed: {@code indexes} are positions in {@code Save.notificationAlerts}. */
public record AlertsLitEvent(List<Integer> indexes) {
}
