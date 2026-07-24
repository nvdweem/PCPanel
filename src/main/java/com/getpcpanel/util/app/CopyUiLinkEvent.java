package com.getpcpanel.util.app;

/**
 * Fired by the tray to copy a one-time UI bootstrap link (carrying a fresh single-use session nonce) to
 * the clipboard, so a user whose browser did not open can paste it manually. Handled by
 * {@link com.getpcpanel.integration.webui.CopyUiLinkService}.
 */
public record CopyUiLinkEvent() {
}
