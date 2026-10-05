package com.getpcpanel.appwindow;

import javax.annotation.Nullable;

/** A platform's window around a web view, run by {@link AppWindowMain}. */
interface WindowBackend {
    /**
     * Opens the window on {@code url} and runs it on the calling thread until it closes. Returns the process exit
     * status: 0 once the window closed, {@link AppWindowMain#EXIT_UNAVAILABLE} when it could not be shown at all.
     */
    int run(String url);

    /** Opens {@code url} (null keeps the page) and raises the window. Safe from any thread, also before it shows. */
    void show(@Nullable String url);

    /** Closes the window, which ends {@link #run}. Safe from any thread, also before it shows. */
    void close();
}
