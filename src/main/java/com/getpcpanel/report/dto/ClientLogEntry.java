package com.getpcpanel.report.dto;

import javax.annotation.Nullable;

/** One browser-console entry, as captured by the UI's rolling buffer. */
public record ClientLogEntry(long timestamp, String level, String message, @Nullable String stack) {
}
