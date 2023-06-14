package com.getpcpanel.spring;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Service;

import javafx.css.Styleable;
import javafx.scene.Node;
import one.util.streamex.StreamEx;

@Service
public class OsHelper {
    public static final String WINDOWS = "windows";
    public static final String LINUX = "linux";
    private static final Set<String> supportedOss = Set.of(WINDOWS, LINUX);
    private final Set<String> toHideClasses;

    public OsHelper() {
        toHideClasses = StreamEx.of(supportedOss).remove(osString()::equals).map(v -> "only-" + v).toSet();
    }

    public boolean notWindows() {
        return !SystemUtils.IS_OS_WINDOWS;
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

    public boolean isSupported(Styleable elem) {
        return toHideClasses.stream().noneMatch(toHideClass -> elem.getStyleClass().contains(toHideClass));
    }

    public void hideUnsupportedChildren(List<Node> children) {
        StreamEx.of(children).remove(this::isSupported).forEach(c -> {
            c.setVisible(false);
            c.setManaged(false);
        });
    }

    public boolean isOs(String os) {
        return StringUtils.equalsAny(os, "*", osString());
    }
}
