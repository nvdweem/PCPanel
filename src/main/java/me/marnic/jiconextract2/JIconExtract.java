package me.marnic.jiconextract2;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

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
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinGDI.BITMAP;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

public class JIconExtract {
    public static BufferedImage getIconForFile(int width, int height, Path file) {
        return getIconForFile(width, height, file.toString());
    }

    public static BufferedImage getIconForFile(int width, int height, File file) {
        return getIconForFile(width, height, file.getAbsolutePath());
    }

    public static BufferedImage getIconForFile(int width, int height, String fileName) {
        HBITMAP hbitmap = getHBITMAPForFile(width, height, fileName);
        BITMAP bitmap = new BITMAP();
        try {
            int s = GDI32.INSTANCE.GetObject(hbitmap, bitmap.size(), bitmap.getPointer());
            if (s > 0) {
                bitmap.read();
                int w = bitmap.bmWidth.intValue();
                int h = bitmap.bmHeight.intValue();
                HDC hdc = User32.INSTANCE.GetDC(null);
                BITMAPINFO bitmapinfo = new BITMAPINFO();
                bitmapinfo.bmiHeader.biSize = bitmapinfo.bmiHeader.size();
                if (0 == GDI32.INSTANCE.GetDIBits(hdc, hbitmap, 0, 0, Pointer.NULL, bitmapinfo, 0))
                    throw new IllegalArgumentException("GetDIBits should not return 0");
                bitmapinfo.read();
                Memory lpPixels = new Memory(bitmapinfo.bmiHeader.biSizeImage);
                bitmapinfo.bmiHeader.biCompression = 0;
                bitmapinfo.bmiHeader.biHeight = -h;
                if (0 == GDI32.INSTANCE.GetDIBits(hdc, hbitmap, 0, bitmapinfo.bmiHeader.biHeight, lpPixels, bitmapinfo, 0))
                    throw new IllegalArgumentException("GetDIBits should not return 0");
                int[] colorArray = lpPixels.getIntArray(0L, w * h);
                BufferedImage bi = new BufferedImage(w, h, 2);
                bi.setRGB(0, 0, w, h, colorArray, 0, w);
                return bi;
            }
        } finally {
            GDI32.INSTANCE.DeleteObject(hbitmap);
        }
        return null;
    }

    public static HBITMAP getHBITMAPForFile(int width, int height, String fileName) {
        HRESULT h1 = Ole32.INSTANCE.CoInitialize(null);
        if (COMUtils.SUCCEEDED(h1)) {
            PointerByReference factory = new PointerByReference();
            HRESULT h2 = Shell32Extra.INSTANCE.SHCreateItemFromParsingName(new WString(fileName), null, new REFIID(new IID("BCC18B79-BA16-442F-80C4-8A59C30C463B")), factory);
            if (COMUtils.SUCCEEDED(h2)) {
                IShellItemImageFactory imageFactory = new IShellItemImageFactory(factory.getValue());
                PointerByReference hbitmapPointer = new PointerByReference();
                HRESULT h3 = imageFactory.GetImage(new SIZEByValue(width, height), 0, hbitmapPointer);
                if (COMUtils.SUCCEEDED(h3)) {
                    HBITMAP bitmap = new HBITMAP(hbitmapPointer.getValue());
                    return bitmap;
                }
                imageFactory.Release();
            }
        }
        return null;
    }
}
