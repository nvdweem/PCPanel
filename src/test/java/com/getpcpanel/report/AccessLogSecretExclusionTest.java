package com.getpcpanel.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The access log records query strings, and it is offered as a bug-report attachment — so any endpoint
 * taking a secret in its query string must be excluded from it, or filing a report would publish that
 * secret. Today that is the bootstrap handshake, whose query carries the single-use session nonce;
 * {@code ShowMainService} strips the same value from its own log line for the same reason.
 */
@DisplayName("Access log (no secret-bearing URL is recorded)")
class AccessLogSecretExclusionTest {
    private static final String BOOTSTRAP_PATH = "/api/auth/bootstrap";

    private static Properties applicationProperties() throws IOException {
        var text = Files.readString(Path.of("src/main/resources/application.properties"));
        var properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }

    @Test
    @DisplayName("the bootstrap path is excluded, so the session nonce never reaches the log")
    void excludesTheBootstrapHandshake() throws IOException {
        var properties = applicationProperties();

        assertTrue(Boolean.parseBoolean(properties.getProperty("quarkus.http.access-log.enabled")),
                "the access log is what this exclusion protects; if it is off, this guard is meaningless");

        var excludePattern = properties.getProperty("quarkus.http.access-log.exclude-pattern");
        assertTrue(excludePattern != null && !excludePattern.isBlank(),
                "quarkus.http.access-log.exclude-pattern must exclude the secret-bearing bootstrap path");

        // Quarkus matches the exclusion against the full normalized path.
        assertTrue(Pattern.compile(excludePattern).matcher(BOOTSTRAP_PATH).matches(),
                () -> "the exclusion '" + excludePattern + "' does not cover " + BOOTSTRAP_PATH);
    }

    @Test
    @DisplayName("the exclusion is narrow — it must not silence the rest of the API")
    void doesNotExcludeOrdinaryPaths() throws IOException {
        var excludePattern = Pattern.compile(applicationProperties().getProperty("quarkus.http.access-log.exclude-pattern"));

        for (var path : new String[] { "/api/devices", "/api/settings", "/api/report", "/api/auth/status", "/" }) {
            assertFalse(excludePattern.matcher(path).matches(),
                    () -> path + " must still be recorded — the access log is the point");
        }
    }
}
