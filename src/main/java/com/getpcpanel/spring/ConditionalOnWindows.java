package com.getpcpanel.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.commons.lang3.SystemUtils;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface ConditionalOnWindows {
    class OnWindowsCondition {
        public static boolean matches() {
            return SystemUtils.IS_OS_WINDOWS;
        }
    }
}
