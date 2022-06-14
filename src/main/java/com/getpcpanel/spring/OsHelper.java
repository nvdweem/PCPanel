package com.getpcpanel.spring;

import java.util.Set;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Service;

import javafx.css.Styleable;
import one.util.streamex.StreamEx;

@Service
public class OsHelper {
    private static final Set<String> supportedOss = Set.of("windows", "linux");
    private final Set<String> toHideClasses;

    public OsHelper() {
        toHideClasses = StreamEx.of(supportedOss).remove(osString()::equals).map(v -> "only-" + v).toSet();
    }

    public boolean notWindows() {
        return !SystemUtils.IS_OS_WINDOWS;
    }

    public String osString() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "windows";
        }
        if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        }
        return "unsupported";
    }

    public boolean isSupported(Styleable elem) {
        return toHideClasses.stream().noneMatch(toHideClass -> elem.getStyleClass().contains(toHideClass));
    }
}
