package com.getpcpanel.volume;

public interface IFocusRedirector {
    boolean handleFocusVolumeRequest(String targetProcess, float volume);

    /**
     * Whether this redirector would claim {@code targetProcess}'s focus volume (i.e.
     * {@link #handleFocusVolumeRequest} would return {@code true}), evaluated without applying any
     * volume change. Used to inspect the deferral decision (e.g. from the dev test harness).
     */
    default boolean controlsFocusApp(String targetProcess) {
        return false;
    }
}
