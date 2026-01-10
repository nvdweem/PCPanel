package com.getpcpanel.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when AWT SystemTray is expected to work:
 * Windows (always) or Linux with X11 (not Wayland).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Conditional(ConditionalOnAwtTray.OnAwtTrayCondition.class)
public @interface ConditionalOnAwtTray {
    class OnAwtTrayCondition implements Condition {
        private static final ConditionalOnX11.OnX11Condition X11_CONDITION = new ConditionalOnX11.OnX11Condition();

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            // Windows always supports AWT tray
            if (SystemUtils.IS_OS_WINDOWS) {
                return true;
            }

            // Linux only supports AWT tray on X11, not Wayland
            if (SystemUtils.IS_OS_LINUX) {
                return X11_CONDITION.matches(context, metadata);
            }

            // macOS and others - let AWT try (may or may not work)
            return true;
        }
    }
}
