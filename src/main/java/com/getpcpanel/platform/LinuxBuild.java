package com.getpcpanel.platform;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.inject.Stereotype;

@Stereotype
@IfBuildProperty(name = "pcpanel.build.os", stringValue = "linux")
@Retention(RUNTIME)
@Target(TYPE)
public @interface LinuxBuild {
}


