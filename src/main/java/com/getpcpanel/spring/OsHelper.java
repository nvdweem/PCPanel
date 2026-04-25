package com.getpcpanel.spring;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OsHelper {
    public static final String WINDOWS = "windows";
    public static final String LINUX = "linux";

    public boolean notWindows() {
        return !SystemUtils.IS_OS_WINDOWS;
    }

    public boolean isLinux() {
        return SystemUtils.IS_OS_LINUX;
    }

    public String osString() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return WINDOWS;
        }
        if (SystemUtils.IS_OS_LINUX) {
            return LINUX;
        }
        return "unsupported";
    }

    public boolean isOs(String os) {
        return StringUtils.equalsAny(os, "*", osString());
    }
}
