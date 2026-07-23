package com.getpcpanel.util.version;

/**
 * The GitHub repository whose releases the version check and self-updater read, hardcoded rather than
 * read from configuration. The update path downloads and runs an installer, so the source it trusts must
 * not be redirectable at runtime: as a {@code @ConfigProperty} it could be pointed at an attacker-
 * controlled repository by a system property, an environment variable, or a
 * {@code config/application.properties} dropped next to the executable. A constant cannot be overridden by
 * any config source. A fork that ships its own releases changes this one value and rebuilds.
 */
public final class UpdateSource {
    /** {@code owner/repo} on github.com whose releases are the update source. */
    public static final String GITHUB_REPO = "nvdweem/PCPanel";

    private UpdateSource() {
    }
}
