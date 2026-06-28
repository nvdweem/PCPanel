package com.getpcpanel.integration.volume;

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

    /**
     * Whether this integration already manages {@code targetProcess}'s volume in general, independent of
     * the focus-redirect setting (e.g. the app is in a Wave Link channel). Used by the "skip otherwise
     * controlled applications" option so focus volume leaves such an app alone.
     */
    default boolean managesFocusApp(String targetProcess) {
        return false;
    }
}
