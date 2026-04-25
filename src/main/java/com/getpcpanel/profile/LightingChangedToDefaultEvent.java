package com.getpcpanel.profile;

/**
 * Fired when lighting is changed back to its default/profile setting.
 * Moved from com.getpcpanel.ui to profile package as part of Quarkus migration.
 */
public record LightingChangedToDefaultEvent(String serialNum) {
}
