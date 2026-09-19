package com.getpcpanel.template.rest.dto;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** The completion entries one level below a path ({@code ""} for the roots). */
@RegisterForReflection(targets = { TemplateCatalogDto.class, TemplateVariableDto.class, TemplateVariableDto[].class })
public record TemplateCatalogDto(String path, List<TemplateVariableDto> items) {
}
