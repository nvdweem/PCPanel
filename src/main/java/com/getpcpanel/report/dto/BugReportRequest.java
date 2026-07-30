package com.getpcpanel.report.dto;

import java.util.List;

import javax.annotation.Nullable;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A bug report as the dialog collected it: the user's own words, plus which of the optional
 * attachments they agreed to include. The client-side lists are captured by the browser (the backend
 * cannot see the UI's console or how a request looked from the browser's side) and are only sent when
 * {@link #includeClientDiagnostics()} is set.
 */
@RegisterForReflection(targets = {
        BugReportRequest.class,
        ClientLogEntry.class, ClientLogEntry[].class,
        ClientRequestFailure.class, ClientRequestFailure[].class })
public record BugReportRequest(
        String summary,
        String expected,
        String steps,
        @Nullable String deviceSerial,
        boolean includeLog,
        boolean includeSystemInfo,
        boolean includeProfile,
        boolean includeClientDiagnostics,
        @Nullable List<ClientLogEntry> consoleEntries,
        @Nullable List<ClientRequestFailure> failedRequests) {
}
