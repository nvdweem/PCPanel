package com.getpcpanel.cpp.linux.pulseaudio;

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

import com.getpcpanel.cpp.linux.ProcessConditionalHelper;

import lombok.extern.log4j.Log4j2;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Conditional(ConditionalOnPulseAudio.OnLinuxCondition.class)
public @interface ConditionalOnPulseAudio {
    @Log4j2
    class OnLinuxCondition implements Condition {
        private static final String PACTL = "pactl";

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (!SystemUtils.IS_OS_LINUX) {
                return false;
            }
            var pactlAvailable = ProcessConditionalHelper.isProcessAvailable(PACTL);
            if (!pactlAvailable) {
                log.error("Pactl is not available, install it first");
                System.exit(1);
            }
            return pactlAvailable;
        }
    }
}
