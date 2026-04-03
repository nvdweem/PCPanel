package com.getpcpanel.rest;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.DeviceType;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.KnobSetting;
import com.getpcpanel.profile.LightingConfig;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.dto.DeviceDto;
import com.getpcpanel.rest.dto.ProfileDto;

import one.util.streamex.StreamEx;

@Path("/api/devices")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;

    @GET
    public List<DeviceDto> listDevices() {
        var save = saveService.get();
        return StreamEx.of(deviceHolder.all())
                       .map(d -> DeviceDto.from(d, save.getDeviceSave(d.getSerialNumber())))
                       .toList();
    }

    @GET
    @Path("/{serial}")
    public DeviceDto getDevice(@PathParam("serial") String serial) {
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        return DeviceDto.from(device, saveService.get().getDeviceSave(serial));
    }

    @PUT
    @Path("/{serial}/name")
    public Response renameDevice(@PathParam("serial") String serial, String name) {
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        device.setDisplayName(name);
        saveService.save();
        return Response.ok().build();
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    @GET
    @Path("/{serial}/profiles")
    public List<ProfileDto> listProfiles(@PathParam("serial") String serial) {
        var deviceSave = getDeviceSave(serial);
        return StreamEx.of(deviceSave.getProfiles()).map(ProfileDto::from).toList();
    }

    @POST
    @Path("/{serial}/profiles")
    public Response createProfile(@PathParam("serial") String serial, String name) {
        var deviceSave = getDeviceSave(serial);
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        var profile = new Profile(name, device.deviceType());
        deviceSave.getProfiles().add(profile);
        saveService.save();
        return Response.ok(ProfileDto.from(profile)).build();
    }

    @DELETE
    @Path("/{serial}/profiles/{name}")
    public Response deleteProfile(@PathParam("serial") String serial, @PathParam("name") String name) {
        var deviceSave = getDeviceSave(serial);
        deviceSave.getProfiles().removeIf(p -> p.getName().equals(name));
        saveService.save();
        return Response.noContent().build();
    }

    @PUT
    @Path("/{serial}/profiles/current")
    public Response switchProfile(@PathParam("serial") String serial, String name) {
        var deviceSave = getDeviceSave(serial);
        deviceSave.setCurrentProfile(name).orElseThrow(() -> new NotFoundException("Profile not found: " + name));
        saveService.save();
        return Response.ok().build();
    }

    // ── Button/Dial assignments ────────────────────────────────────────────────

    @GET
    @Path("/{serial}/profiles/{profile}/buttons/{index}")
    public Commands getButton(@PathParam("serial") String serial,
                               @PathParam("profile") String profileName,
                               @PathParam("index") int index) {
        return getProfile(serial, profileName).getButtonData(index);
    }

    @PUT
    @Path("/{serial}/profiles/{profile}/buttons/{index}")
    public Response setButton(@PathParam("serial") String serial,
                               @PathParam("profile") String profileName,
                               @PathParam("index") int index,
                               Commands commands) {
        getProfile(serial, profileName).setButtonData(index, commands);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/{serial}/profiles/{profile}/dblbuttons/{index}")
    public Commands getDblButton(@PathParam("serial") String serial,
                                  @PathParam("profile") String profileName,
                                  @PathParam("index") int index) {
        return Optional.ofNullable(getProfile(serial, profileName).getDblButtonData(index))
                       .orElse(Commands.EMPTY);
    }

    @PUT
    @Path("/{serial}/profiles/{profile}/dblbuttons/{index}")
    public Response setDblButton(@PathParam("serial") String serial,
                                  @PathParam("profile") String profileName,
                                  @PathParam("index") int index,
                                  Commands commands) {
        getProfile(serial, profileName).setDblButtonData(index, commands);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/{serial}/profiles/{profile}/dials/{index}")
    public Commands getDial(@PathParam("serial") String serial,
                             @PathParam("profile") String profileName,
                             @PathParam("index") int index) {
        return Optional.ofNullable(getProfile(serial, profileName).getDialData(index))
                       .orElse(Commands.EMPTY);
    }

    @PUT
    @Path("/{serial}/profiles/{profile}/dials/{index}")
    public Response setDial(@PathParam("serial") String serial,
                             @PathParam("profile") String profileName,
                             @PathParam("index") int index,
                             Commands commands) {
        getProfile(serial, profileName).setDialData(index, commands);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/{serial}/profiles/{profile}/knobsettings/{index}")
    public KnobSetting getKnobSettings(@PathParam("serial") String serial,
                                        @PathParam("profile") String profileName,
                                        @PathParam("index") int index) {
        return getProfile(serial, profileName).getKnobSettings(index);
    }

    @PUT
    @Path("/{serial}/profiles/{profile}/knobsettings/{index}")
    public Response setKnobSettings(@PathParam("serial") String serial,
                                     @PathParam("profile") String profileName,
                                     @PathParam("index") int index,
                                     KnobSetting settings) {
        var knob = getProfile(serial, profileName).getKnobSettings(index);
        knob.setMinTrim(settings.getMinTrim());
        knob.setMaxTrim(settings.getMaxTrim());
        knob.setLogarithmic(settings.isLogarithmic());
        knob.setOverlayIcon(settings.getOverlayIcon());
        knob.setButtonDebounce(settings.getButtonDebounce());
        saveService.save();
        return Response.ok().build();
    }

    // ── Lighting ──────────────────────────────────────────────────────────────

    @GET
    @Path("/{serial}/lighting")
    public LightingConfig getLighting(@PathParam("serial") String serial) {
        return deviceHolder.getDevice(serial)
                           .map(Device::getSavedLightingConfig)
                           .orElseThrow(NotFoundException::new);
    }

    @PUT
    @Path("/{serial}/lighting")
    public Response setLighting(@PathParam("serial") String serial, LightingConfig config) {
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        device.setSavedLighting(config);
        saveService.save();
        return Response.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DeviceSave getDeviceSave(String serial) {
        var save = saveService.get();
        var deviceSave = save.getDevices().get(serial);
        if (deviceSave == null) {
            throw new NotFoundException("Device not found: " + serial);
        }
        return deviceSave;
    }

    private Profile getProfile(String serial, String profileName) {
        return getDeviceSave(serial).getProfile(profileName)
                                    .orElseThrow(() -> new NotFoundException("Profile not found: " + profileName));
    }
}
