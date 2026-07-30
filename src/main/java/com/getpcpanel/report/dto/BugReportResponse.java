package com.getpcpanel.report.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Where the bundle was written and where to file the report. {@code issueUrl} is a prefilled GitHub
 * issue; the user attaches {@code fileName} to it themselves.
 */
@RegisterForReflection(targets = { BugReportResponse.class, BugReportResponse[].class })
public record BugReportResponse(String fileName, String path, String issueUrl) {
}
