package com.getpcpanel.profile;

/**
 * Fired when a device's current profile has been switched (via the UI, a hardware button or an activation shortcut).
 */
public record ProfileSwitchedEvent(String serial, String profileName) {
}
