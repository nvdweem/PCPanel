package com.getpcpanel.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.context.Dependent;

@Documented
@Dependent
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prototype {
}
