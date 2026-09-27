package com.getpcpanel.integration.sonar.rest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.getpcpanel.integration.sonar.SonarService;
import com.getpcpanel.integration.sonar.rest.dto.SonarStatusDto;
import com.getpcpanel.integration.sonar.rest.dto.SonarStatusDto.SonarChannelDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Sonar status for the config UI. Reads {@link SonarService}'s cached snapshot only — this runs on a UI
 * request thread and must never call Sonar itself.
 */
@Path("/api/sonar")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class SonarResource {
    private final SonarService sonar;

    @Inject
    public SonarResource(SonarService sonar) {
        this.sonar = sonar;
    }

    @GET
    @Path("/status")
    public SonarStatusDto status() {
        var state = sonar.snapshot();
        var channels = new ArrayList<SonarChannelDto>();
        state.levels().forEach((route, level) -> channels.add(new SonarChannelDto(
                route.channel().name(),
                route.mix() == null ? null : route.mix().apiId(),
                level.volume(), level.muted())));
        channels.sort(Comparator.comparing(SonarChannelDto::channel)
                .thenComparing(SonarChannelDto::mix, Comparator.nullsFirst(Comparator.naturalOrder())));
        return new SonarStatusDto(sonar.isEnabled(), sonar.isReady(),
                state.mode() == null ? null : state.mode().name(), List.copyOf(channels));
    }
}
