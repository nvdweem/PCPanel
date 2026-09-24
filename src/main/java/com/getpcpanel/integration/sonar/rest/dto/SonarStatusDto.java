package com.getpcpanel.integration.sonar.rest.dto;

import java.util.List;

import javax.annotation.Nullable;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Sonar status for the config UI. Read from {@link com.getpcpanel.integration.sonar.SonarService}'s
 * cache — this endpoint never calls Sonar itself.
 *
 * @param mode null before the first poll settles a real mode.
 */
@RegisterForReflection(targets = {
        SonarStatusDto.class,
        SonarStatusDto.SonarChannelDto.class, SonarStatusDto.SonarChannelDto[].class })
public record SonarStatusDto(boolean enabled, boolean ready, @Nullable String mode, List<SonarChannelDto> channels) {

    /** @param mix null in Classic mode, which has no mixes — one row per channel there, not per (channel, mix). */
    public record SonarChannelDto(String channel, @Nullable String mix, double volume, boolean muted) {}
}
