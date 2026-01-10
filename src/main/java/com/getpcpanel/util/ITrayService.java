package com.getpcpanel.util;

/**
 * Interface for system tray implementations.
 * Platform-specific implementations handle tray icon display and interaction.
 */
public interface ITrayService {
    /**
     * Initialize the system tray icon.
     * Called automatically via {@code @PostConstruct}.
     */
    void init();

    /**
     * Check if the tray icon is disabled or unavailable.
     * @return true if the tray could not be initialized
     */
    boolean isTrayDisabled();
}
