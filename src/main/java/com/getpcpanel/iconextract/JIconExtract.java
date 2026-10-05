/*
 * Adapted from JIconExtract by MrMarnic — https://github.com/MrMarnic/JIconExtractReloaded
 * Copyright (c) 2019 MrMarnic. Used per the upstream grant: "You are free to use it in your project."
 */
package com.getpcpanel.iconextract;

import java.awt.image.BufferedImage;
import java.io.File;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Guid.IID;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinGDI.BITMAP;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.ptr.PointerByReference;

final class JIconExtract {
    private JIconExtract() {
    }

    public static BufferedImage getIconForFile(int width, int height, File file) {
        return getIconForFile(width, height, file.getAbsolutePath());
    }

    /**
     * The shell's icon for {@code fileName} at the given size, or null. Everything it acquires is given back before
     * it returns — the bitmap, the screen DC, the shell item and the COM initialisation — because the process list
     * runs this for every running process each time it is read.
     */
    public static BufferedImage getIconForFile(int width, int height, String fileName) {
        var hbitmap = getHBITMAPForFile(width, height, fileName);
        if (hbitmap == null) {
            return null;
        }
        try {
            return toImage(hbitmap);
        } finally {
            GDI32.INSTANCE.DeleteObject(hbitmap);
        }
    }

    private static BufferedImage toImage(HBITMAP hbitmap) {
        var bitmap = new BITMAP();
        if (GDI32.INSTANCE.GetObject(hbitmap, bitmap.size(), bitmap.getPointer()) <= 0) {
            return null;
        }
        bitmap.read();
        var w = bitmap.bmWidth.intValue();
        var h = bitmap.bmHeight.intValue();
        var hdc = User32.INSTANCE.GetDC(null);
        try {
            var bitmapinfo = new BITMAPINFO();
            bitmapinfo.bmiHeader.biSize = bitmapinfo.bmiHeader.size();
            if (0 == GDI32.INSTANCE.GetDIBits(hdc, hbitmap, 0, 0, Pointer.NULL, bitmapinfo, 0))
                throw new IllegalArgumentException("GetDIBits should not return 0");
            bitmapinfo.read();
            try (var lpPixels = new Memory(bitmapinfo.bmiHeader.biSizeImage)) {
                bitmapinfo.bmiHeader.biCompression = 0;
                bitmapinfo.bmiHeader.biHeight = -h;
                if (0 == GDI32.INSTANCE.GetDIBits(hdc, hbitmap, 0, bitmapinfo.bmiHeader.biHeight, lpPixels, bitmapinfo, 0))
                    throw new IllegalArgumentException("GetDIBits should not return 0");
                var colorArray = lpPixels.getIntArray(0L, w * h);
                var bi = new BufferedImage(w, h, 2);
                bi.setRGB(0, 0, w, h, colorArray, 0, w);
                return bi;
            }
        } finally {
            User32.INSTANCE.ReleaseDC(null, hdc);
        }
    }

    /** The shell's bitmap for {@code fileName}, which the caller deletes, or null. */
    public static HBITMAP getHBITMAPForFile(int width, int height, String fileName) {
        // S_OK or S_FALSE: this call initialised COM on the thread and must be balanced. A thread that already runs
        // COM multithreaded answers RPC_E_CHANGED_MODE, and the shell calls below work there too.
        var init = Ole32.INSTANCE.CoInitialize(null);
        try {
            var factory = new PointerByReference();
            var h2 = Shell32Extra.INSTANCE.SHCreateItemFromParsingName(new WString(fileName), null, new REFIID(new IID("BCC18B79-BA16-442F-80C4-8A59C30C463B")), factory);
            if (!COMUtils.SUCCEEDED(h2)) {
                return null;
            }
            var imageFactory = new IShellItemImageFactory(factory.getValue());
            try {
                var hbitmapPointer = new PointerByReference();
                var h3 = imageFactory.GetImage(new SIZEByValue(width, height), 0, hbitmapPointer);
                return COMUtils.SUCCEEDED(h3) ? new HBITMAP(hbitmapPointer.getValue()) : null;
            } finally {
                imageFactory.Release();
            }
        } finally {
            if (COMUtils.SUCCEEDED(init)) {
                Ole32.INSTANCE.CoUninitialize();
            }
        }
    }
}
