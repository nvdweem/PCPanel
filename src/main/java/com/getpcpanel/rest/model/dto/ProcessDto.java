package com.getpcpanel.rest.model.dto;

import javax.annotation.Nullable;

public record ProcessDto(int pid, String path, String name, @Nullable String icon) {
}
