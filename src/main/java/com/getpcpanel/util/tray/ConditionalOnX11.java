package com.getpcpanel.util.tray;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Conditional(ConditionalOnX11.OnX11Condition.class)
@interface ConditionalOnX11 {
    class OnX11Condition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var sessionType = System.getenv("XDG_SESSION_TYPE");
            var waylandDisplay = System.getenv("WAYLAND_DISPLAY");
            var display = System.getenv("DISPLAY");

            // Explicit X11 session
            if ("x11".equalsIgnoreCase(sessionType)) {
                return true;
            }

            // DISPLAY set but not Wayland (legacy X11 detection)
            if (StringUtils.isNotBlank(display) && StringUtils.isBlank(waylandDisplay)
                    && !"wayland".equalsIgnoreCase(sessionType)) {
                return true;
            }

            return false;
        }
    }
}
