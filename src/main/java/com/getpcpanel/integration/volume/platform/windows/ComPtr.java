package com.getpcpanel.integration.volume.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.WinNT.HRESULT;

/** A COM interface pointer whose methods are called by vtable index (raw Core Audio calls, no SndCtrl.dll). */
final class ComPtr extends Unknown {
    ComPtr(Pointer p) {
        super(p);
    }

    /** Calls method {@code vtableIndex} (IUnknown's three come first) and returns its HRESULT. */
    int call(int vtableIndex, Object... args) {
        var full = new Object[args.length + 1];
        full[0] = getPointer();
        System.arraycopy(args, 0, full, 1, args.length);
        return ((HRESULT) _invokeNativeObject(vtableIndex, full, HRESULT.class)).intValue();
    }
}
