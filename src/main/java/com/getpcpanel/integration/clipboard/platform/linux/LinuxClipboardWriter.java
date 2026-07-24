package com.getpcpanel.integration.clipboard.platform.linux;

import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.integration.clipboard.platform.ClipboardProcess;
import com.getpcpanel.platform.LinuxBuild;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Linux clipboard writer. There is no dependency-free JNA clipboard on Linux, so it shells out to whatever
 * the desktop provides, trying Wayland's {@code wl-copy} first, then X11's {@code xclip} (validated with an
 * xclip round-trip under Xvfb on CI), then {@code xsel}. If none is installed it logs and gives up — the
 * clipboard action is a convenience, not a hard requirement.
 */
@Log4j2
@Unremovable
@LinuxBuild
@ApplicationScoped
class LinuxClipboardWriter implements ClipboardWriter {
    @Override
    public void setText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (ClipboardProcess.pipe(text, "wl-copy")
                || ClipboardProcess.pipe(text, "xclip", "-selection", "clipboard")
                || ClipboardProcess.pipe(text, "xsel", "--clipboard", "--input")) {
            return;
        }
        log.warn("Unable to set the clipboard: none of wl-copy, xclip or xsel is available on this desktop");
    }
}
