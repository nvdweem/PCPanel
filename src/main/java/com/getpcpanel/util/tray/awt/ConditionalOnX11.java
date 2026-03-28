package com.getpcpanel.util.tray.awt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.StringUtils;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@interface ConditionalOnX11 {
    class OnX11Condition {
        public static boolean matches() {
            var sessionType = System.getenv().get("XDG_SESSION_TYPE");
            var waylandDisplay = System.getenv().get("WAYLAND_DISPLAY");
            var display = System.getenv().get("DISPLAY");

            // Explicit X11 session
            if ("x11".equalsIgnoreCase(sessionType)) {
                return true;
            }

            // DISPLAY set but not Wayland (legacy X11 detection)
            return StringUtils.isNotBlank(display) && StringUtils.isBlank(waylandDisplay)
                    && !"wayland".equalsIgnoreCase(sessionType);
        }
    }
}
