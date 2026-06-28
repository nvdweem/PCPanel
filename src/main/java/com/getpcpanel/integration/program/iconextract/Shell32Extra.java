/*
 * Adapted from JIconExtract by MrMarnic — https://github.com/MrMarnic/JIconExtractReloaded
 * Copyright (c) 2019 MrMarnic. Used per the upstream grant: "You are free to use it in your project."
 */
package com.getpcpanel.integration.program.iconextract;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;

public interface Shell32Extra extends Shell32 {
    Shell32Extra INSTANCE = Native.load("shell32", Shell32Extra.class);

    HRESULT SHCreateItemFromParsingName(WString paramWString, Pointer paramPointer, REFIID paramREFIID, PointerByReference paramPointerByReference);
}
