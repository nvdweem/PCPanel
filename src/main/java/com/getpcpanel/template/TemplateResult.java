package com.getpcpanel.template;

import java.util.List;

import javax.annotation.Nullable;

/**
 * What rendering a template produced.
 *
 * @param literalTags {@code {{ … }}} tags that stayed text because they are not a known variable or function
 * @param error       why a section structure was ignored or the render failed
 */
public record TemplateResult(String output, List<String> literalTags, @Nullable String error) {
}
