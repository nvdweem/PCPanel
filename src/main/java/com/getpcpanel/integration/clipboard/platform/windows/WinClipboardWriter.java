package com.getpcpanel.integration.clipboard.platform.windows;

import java.nio.charset.StandardCharsets;

import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.platform.WindowsBuild;
import com.sun.jna.Native;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * Windows clipboard writer via the Win32 API (JNA, no AWT — the native image's AWT is headless). Follows
 * the documented OpenClipboard → EmptyClipboard → GlobalAlloc(GMEM_MOVEABLE) → GlobalLock/write/GlobalUnlock
 * → SetClipboardData(CF_UNICODETEXT) → CloseClipboard sequence. On success the system owns the HGLOBAL, so
 * it is not freed here. Every failure is logged and swallowed.
 */
@Log4j2
@Unremovable
@WindowsBuild
@ApplicationScoped
class WinClipboardWriter implements ClipboardWriter {
    @Override
    public void setText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        var user32 = WinClipboardUser32.INSTANCE;
        var kernel32 = WinClipboardKernel32.INSTANCE;
        if (!user32.OpenClipboard(null)) {
            log.warn("Unable to open the clipboard (error {})", Native.getLastError());
            return;
        }
        try {
            user32.EmptyClipboard();
            var utf16 = (text + '\0').getBytes(StandardCharsets.UTF_16LE);
            var hMem = kernel32.GlobalAlloc(WinClipboardKernel32.GMEM_MOVEABLE, utf16.length);
            if (hMem == null) {
                log.warn("Unable to allocate clipboard memory (error {})", Native.getLastError());
                return;
            }
            var locked = kernel32.GlobalLock(hMem);
            locked.write(0, utf16, 0, utf16.length);
            kernel32.GlobalUnlock(hMem);
            if (user32.SetClipboardData(WinClipboardUser32.CF_UNICODETEXT, hMem) == null) {
                // The system did not take ownership, so free what we allocated.
                kernel32.GlobalFree(hMem);
                log.warn("SetClipboardData failed (error {})", Native.getLastError());
            }
        } catch (RuntimeException e) {
            log.warn("Unable to set the clipboard", e);
        } finally {
            user32.CloseClipboard();
        }
    }
}
