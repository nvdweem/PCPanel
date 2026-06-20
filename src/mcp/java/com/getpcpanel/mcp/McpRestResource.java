package com.getpcpanel.mcp;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.device.descriptor.DeviceDescriptor;

import io.quarkus.arc.properties.IfBuildProperty;
import lombok.SneakyThrows;

/**
 * Plain-REST mirror of every MCP tool, under {@code /api/mcp/*} on the existing localhost server
 * (127.0.0.1:7654). This is a deliberate second transport: an MCP client has to open an SSE channel
 * and complete a JSON-RPC handshake, which is awkward for an agent that just launched the app it is
 * debugging and wants to poke it immediately. These endpoints need no MCP client and no handshake -
 * an agent can {@code curl} them directly. They delegate to the very same {@code @Tool} beans (the
 * {@code @Tool} annotation does not stop the methods being called normally), so there is no second
 * copy of the logic.
 *
 * <p>Gated identically to the tools: compiled only under the {@code mcp} Maven profile and wired only
 * when {@code pcpanel.mcp.dev=true}. Bound to localhost only.
 */
@ApplicationScoped
@Path("/api/mcp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class McpRestResource {
    @Inject RuntimeInfoTools runtimeInfo;
    @Inject DeviceIntrospectionTools devices;
    @Inject LogTools logs;
    @Inject AudioTools audio;
    @Inject SimulationTools simulation;
    @Inject ObjectMapper objectMapper;

    @GET
    public Index index() {
        return new Index(
                "PCPanel MCP dev tools, mirrored as plain REST. MCP clients can instead use the SSE "
                        + "endpoint at /mcp/sse.",
                List.of(
                        "GET  /api/mcp/runtime-info",
                        "GET  /api/mcp/devices",
                        "GET  /api/mcp/devices/{serial}",
                        "GET  /api/mcp/serial-ports",
                        "GET  /api/mcp/midi-devices",
                        "GET  /api/mcp/logs?level=&limit=&contains=",
                        "GET  /api/mcp/last-error",
                        "GET  /api/mcp/audio-state?filter=",
                        "POST /api/mcp/simulate/analog   {serial,index,value}",
                        "POST /api/mcp/simulate/button   {serial,index,pressed}",
                        "POST /api/mcp/simulate/deej     {serial,line}",
                        "POST /api/mcp/simulate/midi     {serial,status,data1,data2}",
                        "POST /api/mcp/virtual-device    {serial,descriptor}",
                        "DELETE /api/mcp/virtual-device/{serial}"));
    }

    // ── Read-only introspection ────────────────────────────────────────────────

    @GET @Path("/runtime-info")
    public RuntimeInfoTools.RuntimeInfo runtimeInfo() {
        return runtimeInfo.pcpanel_runtime_info();
    }

    @GET @Path("/devices")
    public Object listDevices() {
        return devices.pcpanel_list_devices();
    }

    @GET @Path("/devices/{serial}")
    public DeviceIntrospectionTools.DeviceSnapshot getDevice(@PathParam("serial") String serial) {
        return devices.pcpanel_get_device(serial);
    }

    @GET @Path("/serial-ports")
    public DeviceIntrospectionTools.SerialPorts serialPorts() {
        return devices.pcpanel_list_serial_ports();
    }

    @GET @Path("/midi-devices")
    public DeviceIntrospectionTools.MidiDevices midiDevices() {
        return devices.pcpanel_list_midi_devices();
    }

    @GET @Path("/logs")
    public LogTools.RecentLogs logs(@QueryParam("level") String level, @QueryParam("limit") Integer limit,
            @QueryParam("contains") String contains) {
        return logs.pcpanel_recent_logs(level, limit, contains);
    }

    @GET @Path("/last-error")
    public LogTools.LastError lastError() {
        return logs.pcpanel_last_error();
    }

    @GET @Path("/audio-state")
    public AudioTools.AudioState audioState(@QueryParam("filter") String filter) {
        return audio.pcpanel_get_audio_state(filter);
    }

    // ── Simulation + virtual devices ───────────────────────────────────────────

    @POST @Path("/simulate/analog")
    public SimulationTools.Ack simulateAnalog(AnalogRequest r) {
        return simulation.pcpanel_simulate_analog(r.serial(), r.index(), r.value());
    }

    @POST @Path("/simulate/button")
    public SimulationTools.Ack simulateButton(ButtonRequest r) {
        return simulation.pcpanel_simulate_button(r.serial(), r.index(), r.pressed());
    }

    @POST @Path("/simulate/deej")
    public SimulationTools.DeejResult simulateDeej(DeejRequest r) {
        return simulation.pcpanel_simulate_deej_line(r.serial(), r.line());
    }

    @POST @Path("/simulate/midi")
    public SimulationTools.MidiResult simulateMidi(MidiRequest r) {
        return simulation.pcpanel_simulate_midi(r.serial(), r.status(), r.data1(), r.data2());
    }

    @POST @Path("/virtual-device")
    @SneakyThrows
    public SimulationTools.Ack createVirtualDevice(CreateVirtualDeviceRequest r) {
        var json = objectMapper.writeValueAsString(r.descriptor());
        return simulation.pcpanel_create_virtual_device(r.serial(), json);
    }

    @DELETE @Path("/virtual-device/{serial}")
    public SimulationTools.Ack removeVirtualDevice(@PathParam("serial") String serial) {
        return simulation.pcpanel_remove_virtual_device(serial);
    }

    // ── Request bodies ─────────────────────────────────────────────────────────

    public record Index(String about, List<String> endpoints) {
    }

    public record AnalogRequest(String serial, int index, int value) {
    }

    public record ButtonRequest(String serial, int index, boolean pressed) {
    }

    public record DeejRequest(String serial, String line) {
    }

    public record MidiRequest(String serial, int status, int data1, int data2) {
    }

    public record CreateVirtualDeviceRequest(String serial, DeviceDescriptor descriptor) {
    }
}
