package com.getpcpanel.spring;

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

import io.reactivex.annotations.NonNull;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
@Conditional(ConditionalOnWayland.OnWaylandCondition.class)
public @interface ConditionalOnWayland {
    class OnWaylandCondition implements Condition {
        private static final String WAYLAND = "wayland";

        @Override
        public boolean matches(@NonNull ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
            var xdgSessionType = context.getEnvironment().getProperty("XDG_SESSION_TYPE");
            var waylandDisplay = context.getEnvironment().getProperty("WAYLAND_DISPLAY");

            return StringUtils.isNotBlank(waylandDisplay)
                    || StringUtils.equalsIgnoreCase(xdgSessionType, WAYLAND);
        }
    }
}
