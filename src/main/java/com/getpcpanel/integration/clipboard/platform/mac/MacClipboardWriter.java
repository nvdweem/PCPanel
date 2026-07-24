package com.getpcpanel.integration.clipboard.platform.mac;

import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.integration.clipboard.platform.ClipboardProcess;
import com.getpcpanel.platform.MacBuild;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * macOS clipboard writer via {@code pbcopy} (validated with a pbcopy/pbpaste round-trip on a CI runner).
 * No AWT — the macOS native image has none.
 */
@Log4j2
@Unremovable
@MacBuild
@ApplicationScoped
class MacClipboardWriter implements ClipboardWriter {
    @Override
    public void setText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!ClipboardProcess.pipe(text, "pbcopy")) {
            log.warn("Unable to set the clipboard via pbcopy");
        }
    }
}
