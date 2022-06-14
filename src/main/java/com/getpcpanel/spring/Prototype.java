package com.getpcpanel.spring;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Scope;

@Documented
@Scope(SCOPE_PROTOTYPE)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prototype {
}
