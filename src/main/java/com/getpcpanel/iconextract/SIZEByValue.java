package com.getpcpanel.iconextract;

import com.sun.jna.Structure.ByValue;
import com.sun.jna.platform.win32.WinUser.SIZE;

public class SIZEByValue extends SIZE implements ByValue {
    public SIZEByValue(int w, int h) {
        super(w, h);
    }
}
