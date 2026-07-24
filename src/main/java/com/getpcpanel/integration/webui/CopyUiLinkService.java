package com.getpcpanel.integration.webui;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.rest.auth.SessionTokenService;
import com.getpcpanel.util.app.CopyUiLinkEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Copies a one-time UI bootstrap link to the clipboard on a {@link CopyUiLinkEvent} (from the tray), for a
 * user whose browser did not open. Mints a fresh single-use nonce each time, so the link is only good for
 * one redemption and only lands on the clipboard when the user explicitly asks for it.
 */
@Log4j2
@ApplicationScoped
public class CopyUiLinkService {
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Inject SessionTokenService sessionTokens;
    @Inject Instance<ClipboardWriter> clipboard;

    public void onCopyUiLink(@Observes CopyUiLinkEvent event) {
        if (clipboard.isUnsatisfied()) {
            log.warn("No clipboard writer on this platform; cannot copy the UI link");
            return;
        }
        var nonce = sessionTokens.issueNonce();
        clipboard.get().setText("http://localhost:" + port + "/api/auth/bootstrap?nonce=" + nonce);
        log.info("Copied a one-time UI link to the clipboard");
    }
}
