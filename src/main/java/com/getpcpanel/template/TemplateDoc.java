package com.getpcpanel.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** What a template variable means, shown in the editor's variable completion. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.RECORD_COMPONENT })
public @interface TemplateDoc {
    String value();
}
