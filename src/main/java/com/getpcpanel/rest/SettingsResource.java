package com.getpcpanel.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.getpcpanel.integration.mqtt.MqttService;
import com.getpcpanel.profile.dto.SaveBackup;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.integration.discord.dto.DiscordSettings;
import com.getpcpanel.integration.mqtt.dto.MqttSettings;
import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.integration.wavelink.dto.WaveLinkSettings;
import com.getpcpanel.rest.model.dto.HistoryDto;
import com.getpcpanel.rest.model.dto.SettingsDto;
import com.getpcpanel.util.SecretMasking;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/settings")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {
    @Inject SaveService saveService;
    @Inject MqttService mqttService;

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
    @Path("/backups")
    public List<SaveBackup> getBackups() {
        return saveService.backups();
    }

    @POST
    @Path("/backups/{name}/restore")
    public Response restoreBackup(@PathParam("name") String name) throws IOException {
        return saveService.restore(name) ? Response.ok().build() : Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/history")
    public HistoryDto getHistory() {
        return new HistoryDto(saveService.canUndo(), saveService.canRedo(), List.of());
    }

    /** The user moved away from what they were editing: the next change is a new undo step. */
    @POST
    @Path("/history/checkpoint")
    public Response checkpoint() {
        saveService.checkpoint();
        return Response.ok().build();
    }

    @POST
    @Path("/undo")
    public HistoryDto undo() {
        var done = saveService.undo();
        return new HistoryDto(saveService.canUndo(), saveService.canRedo(), done ? saveService.lastChange() : List.of());
    }

    @POST
    @Path("/redo")
    public HistoryDto redo() {
        var done = saveService.redo();
        return new HistoryDto(saveService.canUndo(), saveService.canRedo(), done ? saveService.lastChange() : List.of());
    }

    @GET
    @Path("/mqtt")
    public MqttSettings getMqttSettings() {
        return SecretMasking.mask(saveService.get().getMqtt());
    }

    @PUT
    @Path("/mqtt")
    public Response updateMqttSettings(MqttSettings settings) {
        var save = saveService.get();
        save.setMqtt(SecretMasking.unmask(settings, save.getMqtt()));
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/mqtt/status")
    public Map<String, Boolean> getMqttStatus() {
        return Map.of("connected", mqttService.isConnected());
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

    @GET
    @Path("/sonar")
    public SonarSettings getSonarSettings() {
        return saveService.get().getSonar();
    }

    @PUT
    @Path("/sonar")
    public Response updateSonarSettings(SonarSettings settings) {
        saveService.get().setSonar(settings);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/discord")
    public DiscordSettings getDiscordSettings() {
        return SecretMasking.mask(saveService.get().getDiscord());
    }

    @PUT
    @Path("/discord")
    public Response updateDiscordSettings(DiscordSettings settings) {
        var save = saveService.get();
        save.setDiscord(SecretMasking.unmask(settings, save.getDiscord()));
        saveService.save();
        return Response.ok().build();
    }
}
