package com.getpcpanel.rest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Profile names, device serials and Deej ids are user-chosen free text that the Angular device service
 * interpolates straight into a request path. The browser resolves that path before sending it, so an
 * unencoded name does not reach the server as written: a {@code #} starts the fragment and is dropped
 * entirely, a {@code ?} starts the query, a {@code /} invents a segment. The request then addresses an
 * endpoint that does not exist and 404s — for every save, forever, because the name lives in the save
 * file (issue #150).
 *
 * <p>The frontend has no test runner wired into the build, and the failure is invisible in review: an
 * encoded and an unencoded interpolation differ by one call and behave identically for every name
 * without a reserved character. So this reads the source and pins the rule — every interpolated path
 * segment goes through the encoding helper — the same way {@link NativeBuildArgsParityTest} pins the
 * native build args.
 */
@DisplayName("The device service percent-encodes every interpolated URL path segment")
class DeviceServiceUrlEncodingTest {
    private static final Path SERVICE = Path.of("src/main/webui/src/app/services/device.service.ts");
    /** The helper every interpolated segment must go through. */
    private static final String ENCODER = "seg";
    /** Bare interpolations that name a constant base path rather than a value, so they carry no user text. */
    private static final List<String> LITERAL_BASES = List.of("this.base", "this.serialBase");

    /** Every {@code ${...}} in a line, so each can be checked for the helper. */
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([^{}]*)}");
    /** A line issuing an HTTP call, i.e. one that carries a request path. */
    private static final Pattern HTTP_CALL = Pattern.compile("this\\.http\\.\\w+");

    @Test
    @DisplayName("no request path interpolates a value without encoding it")
    void everyInterpolatedSegmentIsEncoded() {
        var unencoded = new ArrayList<String>();
        var checked = 0;

        var lines = read(SERVICE).split("\n");
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (!HTTP_CALL.matcher(line).find()) {
                continue;
            }
            var matcher = INTERPOLATION.matcher(line);
            while (matcher.find()) {
                var expression = matcher.group(1).trim();
                if (LITERAL_BASES.contains(expression)) {
                    continue;
                }
                checked++;
                if (!expression.startsWith(ENCODER + "(")) {
                    unencoded.add((i + 1) + ": ${" + expression + "}");
                }
            }
        }

        assertTrue(checked > 15, "Suspiciously few interpolated segments found in " + SERVICE + ": " + checked);
        if (!unencoded.isEmpty()) {
            fail(("""
                    These request paths interpolate a value without %s(), so a name containing #, ? or / \
                    addresses the wrong endpoint and the request 404s:
                    %s""").formatted(ENCODER, String.join("\n", unencoded)));
        }
    }

    private static String read(Path relative) {
        var path = Path.of(System.getProperty("user.dir")).resolve(relative);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }
}
