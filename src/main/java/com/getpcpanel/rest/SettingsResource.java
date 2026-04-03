package com.getpcpanel.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.getpcpanel.profile.MqttSettings;
import com.getpcpanel.profile.OSCConnectionInfo;
import com.getpcpanel.profile.Save;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.WaveLinkSettings;
import com.getpcpanel.rest.dto.SettingsDto;

@Path("/api/settings")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {
    @Inject SaveService saveService;

    @GET
    public SettingsDto getSettings() {
        return SettingsDto.from(saveService.get());
    }

    @PUT
    public Response updateSettings(SettingsDto dto) {
        var save = saveService.get();
        dto.applyTo(save);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/mqtt")
    public MqttSettings getMqttSettings() {
        return saveService.get().getMqtt();
    }

    @PUT
    @Path("/mqtt")
    public Response updateMqttSettings(MqttSettings settings) {
        saveService.get().setMqtt(settings);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/wavelink")
    public WaveLinkSettings getWaveLinkSettings() {
        return saveService.get().getWaveLink();
    }

    @PUT
    @Path("/wavelink")
    public Response updateWaveLinkSettings(WaveLinkSettings settings) {
        saveService.get().setWaveLink(settings);
        saveService.save();
        return Response.ok().build();
    }
}
