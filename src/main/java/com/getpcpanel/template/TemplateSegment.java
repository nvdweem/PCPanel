package com.getpcpanel.template;

/**
 * One piece of a rendered template: text the user wrote ({@code literal}), or what a {@code {{ … }}} tag produced.
 * A consumer that hands the result to an interpreter (a shell) escapes the tag output and keeps the user's own text
 * as written.
 */
public record TemplateSegment(String text, boolean literal) {
    public static TemplateSegment literal(String text) {
        return new TemplateSegment(text, true);
    }

    public static TemplateSegment value(String text) {
        return new TemplateSegment(text, false);
    }
}
