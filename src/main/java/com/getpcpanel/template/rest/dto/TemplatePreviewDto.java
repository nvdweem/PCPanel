package com.getpcpanel.template.rest.dto;

import java.util.List;

import javax.annotation.Nullable;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @param output      the rendered text
 * @param literalTags {@code {{ … }}} tags that stayed text because they are not a known variable or function
 * @param error       why a section structure was ignored or the render failed
 */
@RegisterForReflection(targets = { TemplatePreviewDto.class, String[].class })
public record TemplatePreviewDto(String output, List<String> literalTags, @Nullable String error) {
}
