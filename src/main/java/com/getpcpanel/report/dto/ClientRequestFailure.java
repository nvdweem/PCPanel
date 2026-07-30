package com.getpcpanel.report.dto;

import javax.annotation.Nullable;

/**
 * A failed HTTP call as the <em>browser</em> saw it. Paired with the server's access log, this is what
 * separates a request the backend rejected from one that never arrived in the form the UI intended —
 * the two records disagreeing is itself the diagnosis.
 */
public record ClientRequestFailure(long timestamp, String method, String url, int status, @Nullable String statusText,
                                   @Nullable String responseSnippet) {
}
