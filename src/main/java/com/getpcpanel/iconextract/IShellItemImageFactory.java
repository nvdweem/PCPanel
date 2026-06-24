/*
 * Adapted from JIconExtract by MrMarnic — https://github.com/MrMarnic/JIconExtractReloaded
 * Copyright (c) 2019 MrMarnic. Used per the upstream grant: "You are free to use it in your project."
 */
package com.getpcpanel.iconextract;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

public class IShellItemImageFactory extends Unknown {
    public IShellItemImageFactory(Pointer pvInstance) {
        super(pvInstance);
    }

    public HRESULT GetImage(SIZEByValue size, int flags, PointerByReference bitmap) {
        return (HRESULT) _invokeNativeObject(3, new Object[] { getPointer(), size, flags, bitmap }, HRESULT.class);
    }
}
