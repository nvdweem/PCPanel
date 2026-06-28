package com.getpcpanel.device.rest;

import java.util.List;

import com.getpcpanel.device.provider.DeviceProviderRegistry;
import com.getpcpanel.device.provider.deej.DeejSerialProvider;
import com.getpcpanel.rest.model.dto.AddDeejDeviceDto;
import com.getpcpanel.rest.model.dto.SerialPortDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import one.util.streamex.StreamEx;

/**
 * REST surface for serial (Deej) devices: list available ports, add a Deej device by port + baud,
 * and remove one. Wired to the {@link DeejSerialProvider} through the
 * {@link DeviceProviderRegistry}.
 */
@Path("/api/serial")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SerialResource {
    @Inject DeviceProviderRegistry registry;

    private DeejSerialProvider deej() {
        return registry.find(DeejSerialProvider.PROVIDER_ID, DeejSerialProvider.class)
                       .orElseThrow(() -> new ServiceUnavailableException("Deej provider is not available"));
    }

    @GET
    @Path("/ports")
    public List<SerialPortDto> listPorts() {
        return StreamEx.of(deej().listPorts())
                       .map(p -> new SerialPortDto(p.port(), p.description()))
                       .toList();
    }

    @POST
    @Path("/deej")
    public Response addDeej(AddDeejDeviceDto request) {
        var id = deej().addManual(request.port(), request.baud(), request.noiseReduction(), request.name());
        return Response.ok(id).build();
    }

    @DELETE
    @Path("/deej/{id}")
    public Response removeDeej(@PathParam("id") String id) {
        deej().removeManual(id);
        return Response.noContent().build();
    }
}
