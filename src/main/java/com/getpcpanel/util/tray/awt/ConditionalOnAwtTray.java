package com.getpcpanel.util.tray.awt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.SystemUtils;

import com.getpcpanel.util.tray.awt.ConditionalOnX11.OnX11Condition;

/**
 * Condition that matches when AWT SystemTray is expected to work:
 * Windows (always) or Linux with X11 (not Wayland).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ConditionalOnAwtTray {
    class OnAwtTrayCondition {
        public static boolean matches() {
            // Windows always supports AWT tray
            if (SystemUtils.IS_OS_WINDOWS) {
                return true;
            }

            // Linux only supports AWT tray on X11, not Wayland
            if (SystemUtils.IS_OS_LINUX) {
                return OnX11Condition.matches();
            }

            // macOS and others - let AWT try (may or may not work)
            return true;
        }
    }
}
