package com.getpcpanel.device.rest;

import java.util.List;

import com.getpcpanel.device.provider.DeviceProviderRegistry;
import com.getpcpanel.device.provider.midi.MidiProvider;
import com.getpcpanel.rest.model.dto.MidiDeviceDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import one.util.streamex.StreamEx;

/**
 * REST surface for MIDI devices: list detected MIDI inputs (for visibility) and trigger a rescan.
 * MIDI is auto-discovered ({@code DiscoveryMode.AUTO}) so no manual add is required. Wired to the
 * {@link MidiProvider} through the {@link DeviceProviderRegistry}.
 */
@Path("/api/midi")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class MidiResource {
    @Inject DeviceProviderRegistry registry;

    private MidiProvider midi() {
        return registry.find(MidiProvider.PROVIDER_ID, MidiProvider.class)
                       .orElseThrow(() -> new ServiceUnavailableException("MIDI provider is not available"));
    }

    @GET
    @Path("/devices")
    public List<MidiDeviceDto> listDevices() {
        return StreamEx.of(midi().detected())
                       .map(d -> new MidiDeviceDto(d.id(), d.name(), d.connected()))
                       .toList();
    }

    @POST
    @Path("/rescan")
    public Response rescan() {
        midi().rescan();
        return Response.noContent().build();
    }
}
