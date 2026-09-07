package com.getpcpanel.rest.model.dto;

/**
 * Whether PCPanel is registered to start when the user signs in to Windows.
 *
 * @param supported    the running build can manage the registration (an installed Windows build)
 * @param enabled      some startup registration exists: the {@code HKCU\Run} value or the elevated task
 * @param elevatedTask the installer's "run as administrator" scheduled task exists; the registration is
 *                     then the installer's to change, and the UI shows the switch on but read-only
 */
public record AutostartStateDto(boolean supported, boolean enabled, boolean elevatedTask) {
}
