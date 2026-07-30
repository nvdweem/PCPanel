package com.getpcpanel.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.report.dto.BugReportRequest;
import com.getpcpanel.util.version.UpdateSource;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Builds the prefilled "new issue" link that the dialog opens. GitHub takes the issue body as a query
 * parameter, and a browser will not follow an arbitrarily long URL, so the body is bounded: the
 * identifying block (version, OS, device, bundle name) is never dropped, and the user's free text is
 * shortened to fit around it. Nothing is lost by that — the bundle's {@code report.md} always carries
 * the full text, and the truncation marker points the reader at it.
 */
@ApplicationScoped
public class BugReportUrlBuilder {
    /** Comfortably inside what browsers and GitHub accept for a URL, with room for the other parameters. */
    static final int MAX_BODY = 6000;
    /** Per-field ceiling applied before falling back to the short body. */
    static final int TRUNCATED_FIELD = 600;
    private static final int MAX_TITLE = 80;
    private static final String MORE = "\n\n_(shortened — the full text is in the attached bundle)_";

    public String build(BugReportRequest request, String version, String os, String deviceLabel, String bundleName) {
        var context = context(version, os, deviceLabel, bundleName);
        var body = body(request, context, Integer.MAX_VALUE);
        if (encodedLength(body) > MAX_BODY) {
            body = body(request, context, TRUNCATED_FIELD);
        }
        if (encodedLength(body) > MAX_BODY) {
            body = context + "\n\nThe description is in the attached bundle.\n";
        }
        return "https://github.com/" + UpdateSource.GITHUB_REPO + "/issues/new"
                + "?labels=bug"
                + "&title=" + encode(title(request))
                + "&body=" + encode(body);
    }

    static String title(BugReportRequest request) {
        var summary = StringUtils.normalizeSpace(StringUtils.defaultString(request.summary()));
        if (StringUtils.isBlank(summary)) {
            return "Bug report";
        }
        return summary.length() <= MAX_TITLE ? summary : summary.substring(0, MAX_TITLE - 1) + "…";
    }

    private static String body(BugReportRequest request, String context, int fieldLimit) {
        return """
                **Describe the bug:**

                %s

                **Steps to reproduce:**

                %s

                **Expected behavior:**

                %s

                %s""".formatted(
                clamp(request.summary(), fieldLimit),
                clamp(request.steps(), fieldLimit),
                clamp(request.expected(), fieldLimit),
                context);
    }

    private static String context(String version, String os, String deviceLabel, String bundleName) {
        return """
                **Additional context:**

                | | |
                |---|---|
                | Version | %s |
                | OS | %s |
                | Device | %s |

                Diagnostics: please attach `%s` — it was saved to the reports folder.
                """.formatted(version, os, StringUtils.defaultIfBlank(deviceLabel, "not device-specific"), bundleName);
    }

    private static String clamp(String value, int limit) {
        var text = StringUtils.defaultIfBlank(value, "_(not provided)_").strip();
        return text.length() <= limit ? text : text.substring(0, limit) + MORE;
    }

    private static int encodedLength(String body) {
        return encode(body).length();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
