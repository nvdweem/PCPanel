package com.getpcpanel.util;

/** Request to reveal a folder in the OS file manager (handled by {@link ShowMainService}). */
public record OpenFolderEvent(String path) {
}
