package com.getpcpanel.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConditionalOnWayland {
    class OnWaylandCondition {
        private static final String WAYLAND = "wayland";

        public static boolean matches() {
            var xdgSessionType = System.getenv().get("XDG_SESSION_TYPE");
            var waylandDisplay = System.getenv().get("WAYLAND_DISPLAY");

            return StringUtils.isNotBlank(waylandDisplay) || Strings.CI.equals(xdgSessionType, (CharSequence) WAYLAND);
        }
    }
}
