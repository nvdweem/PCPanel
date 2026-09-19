package com.getpcpanel.template;

import javax.annotation.Nullable;

import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.util.ValueInterpolator;

/** Template rendering for code that is not a CDI bean, such as commands. */
public final class Templates {
    private Templates() {
    }

    /**
     * Renders a command's template field with {@code value} as {@code {{ value }}}. Text without tags is returned
     * as-is; if the template engine is unavailable or the render fails, the field falls back to plain
     * {@code {{ value }}} substitution.
     */
    @Nullable
    public static String renderValue(@Nullable String template, TemplateScope scope, double value) {
        if (!TemplateService.hasTags(template)) {
            return template;
        }
        return CdiHelper.getOptionalBean(TemplateService.class)
                        .map(service -> service.render(template, scope.withValue(TemplateFunctions.normalize(value)), () -> ValueInterpolator.interpolate(template, value)))
                        .orElseGet(() -> ValueInterpolator.interpolate(template, value));
    }

    /** Renders a template field that has no value of its own (a button action). */
    @Nullable
    public static String render(@Nullable String template, TemplateScope scope) {
        if (!TemplateService.hasTags(template)) {
            return template;
        }
        return CdiHelper.getOptionalBean(TemplateService.class).map(service -> service.render(template, scope, () -> template)).orElse(template);
    }
}
