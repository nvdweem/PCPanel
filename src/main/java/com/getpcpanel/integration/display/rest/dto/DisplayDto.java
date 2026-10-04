package com.getpcpanel.integration.display.rest.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** A monitor Turn displays off can switch on its own; {@code id} is what the command stores. */
@RegisterForReflection(targets = { DisplayDto.class, DisplayDto[].class })
public record DisplayDto(String id, String name) {
}
