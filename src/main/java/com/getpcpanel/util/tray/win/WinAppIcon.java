package com.getpcpanel.util.tray.win;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.annotation.Nullable;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinDef.HICON;

/**
 * The PCPanel icon as a Win32 {@link HICON}, built from the bundled multi-size {@code app-icon.ico}. Shared by the
 * tray and the app window, which runs as a process of its own without the application's logging, so this reports
 * nothing and answers null on any problem.
 */
public final class WinAppIcon {
    private static final String ICON_RESOURCE = "/assets/app-icon.ico";

    private WinAppIcon() {
    }

    /**
     * Parses the bundled {@code .ico} and builds an icon from the directory entry whose size is closest to
     * {@code desired}, via {@code CreateIconFromResourceEx}.
     */
    public static @Nullable HICON load(int desired) {
        try (InputStream in = WinAppIcon.class.getResourceAsStream(ICON_RESOURCE)) {
            if (in == null) {
                return null;
            }
            var ico = in.readAllBytes();
            var bb = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
            var count = bb.getShort(4) & 0xFFFF;
            if (count <= 0 || ico.length < 6 + count * 16) {
                return null;
            }
            var bestOffset = -1;
            var bestSize = 0;
            var bestWidth = 0;
            var bestScore = Integer.MAX_VALUE;
            for (var i = 0; i < count; i++) {
                var off = 6 + i * 16;
                var w = ico[off] & 0xFF;
                if (w == 0) {
                    w = 256;
                }
                var bytesInRes = bb.getInt(off + 8);
                var imageOffset = bb.getInt(off + 12);
                if (imageOffset < 0 || bytesInRes <= 0 || imageOffset + bytesInRes > ico.length) {
                    continue;
                }
                var score = Math.abs(w - desired);
                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = imageOffset;
                    bestSize = bytesInRes;
                    bestWidth = w;
                }
            }
            if (bestOffset < 0) {
                return null;
            }
            try (var mem = new Memory(bestSize)) {
                mem.write(0, ico, bestOffset, bestSize);
                return WinUser32Ext.INSTANCE.CreateIconFromResourceEx(mem, bestSize, true, 0x00030000,
                        bestWidth, bestWidth, WinUser32Ext.LR_DEFAULTCOLOR);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
