/*
 * Adapted from JIconExtract by MrMarnic — https://github.com/MrMarnic/JIconExtractReloaded
 * Copyright (c) 2019 MrMarnic. Used per the upstream grant: "You are free to use it in your project."
 */
package com.getpcpanel.integration.program.iconextract;

import com.sun.jna.Structure.ByValue;
import com.sun.jna.platform.win32.WinUser.SIZE;

public class SIZEByValue extends SIZE implements ByValue {
    public SIZEByValue(int w, int h) {
        super(w, h);
    }
}
