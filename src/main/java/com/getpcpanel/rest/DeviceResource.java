package com.getpcpanel.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.device.Device;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.LightingChangedToDefaultEvent;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.ProfileSwitchedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.rest.EventBroadcaster.AssignmentChangedEvent;
import com.getpcpanel.rest.EventBroadcaster.AssignmentChangedEvent.Kinds;
import com.getpcpanel.rest.EventBroadcaster.DeviceRenamedEvent;
import com.getpcpanel.rest.EventBroadcaster.LightingChangedEvent;
import com.getpcpanel.rest.EventBroadcaster.KnobSettingChangedEvent;
import com.getpcpanel.rest.model.dto.ControlAssignmentsUpdateDto;
import com.getpcpanel.rest.model.dto.DeviceDto;
import com.getpcpanel.rest.model.dto.ProfileDto;
import com.getpcpanel.rest.model.dto.ProfileSettingsDto;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@Path("/api/devices")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeviceResource {
    @Inject DeviceHolder deviceHolder;
    @Inject SaveService saveService;
    @Inject Event<Object> eventBus;

    @GET
    public List<DeviceDto> listDevices() {
        var save = saveService.get();
        var live = StreamEx.of(deviceHolder.all())
                           .map(d -> DeviceDto.from(d, save.getDeviceSave(d.getSerialNumber())))
                           .toList();
        var liveSerials = StreamEx.of(live).map(DeviceDto::serial).toSet();
        // Union of live devices (connected=true) and persisted-but-offline devices (connected=false).
        var offline = EntryStream.of(save.getDevices())
                                 .filterKeys(serial -> !liveSerials.contains(serial))
                                 .mapKeyValue((serial, ds) -> DeviceDto.from(serial, ds, ds.getCapabilities()))
                                 .toList();
        return StreamEx.of(live).append(offline).toList();
    }

    @GET
    @Path("/{serial}")
    public DeviceDto getDevice(@PathParam("serial") String serial) {
        return deviceHolder.getDevice(serial)
                           .map(device -> DeviceDto.from(device, saveService.get().getDeviceSave(serial)))
                           .orElseGet(() -> {
                               var deviceSave = getDeviceSave(serial);
                               return DeviceDto.from(serial, deviceSave, deviceSave.getCapabilities());
                           });
    }

    @PUT
    @Path("/{serial}/name")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response renameDevice(@PathParam("serial") String serial, String name) {
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        device.setDisplayName(name);
        saveService.save();
        eventBus.fire(new DeviceRenamedEvent(serial, name));
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
    @Consumes(MediaType.TEXT_PLAIN)
    public Response createProfile(@PathParam("serial") String serial, String name) {
        var deviceSave = getDeviceSave(serial);
        var profile = new Profile(name, defaultLightingFor(deviceSave));
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
    @Path("/{serial}/profiles/{name}/rename")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response renameProfile(@PathParam("serial") String serial, @PathParam("name") String name, String newName) {
        var trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var deviceSave = getDeviceSave(serial);
        var profile = deviceSave.getProfile(name).orElseThrow(() -> new NotFoundException("Profile not found: " + name));
        if (name.equals(trimmed)) {
            return Response.ok().build();
        }
        if (deviceSave.getProfile(trimmed).isPresent()) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        profile.setName(trimmed);
        // Keep the current-profile pointer valid when the active profile is the one renamed.
        if (name.equals(deviceSave.getCurrentProfileName())) {
            deviceSave.setCurrentProfileName(trimmed);
        }
        saveService.save();
        return Response.ok().build();
    }

    @PUT
    @Path("/{serial}/profiles/current")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response switchProfile(@PathParam("serial") String serial, String name) {
        var deviceSave = getDeviceSave(serial);
        deviceSave.getProfile(name).orElseThrow(() -> new NotFoundException("Profile not found: " + name));
        deviceHolder.getDevice(serial).ifPresentOrElse(
                device -> device.switchProfile(name),
                () -> {
                    deviceSave.setCurrentProfile(name);
                    saveService.save();
                    eventBus.fire(new ProfileSwitchedEvent(serial, name));
                });
        return Response.ok().build();
    }

    @PUT
    @Path("/{serial}/profiles/order")
    public Response reorderProfiles(@PathParam("serial") String serial, List<String> order) {
        var deviceSave = getDeviceSave(serial);
        var current = deviceSave.getProfiles();
        var byName = StreamEx.of(current).toMap(Profile::getName, p -> p, (a, b) -> a);
        var reordered = new ArrayList<Profile>(current.size());
        for (var name : order) {
            var p = byName.get(name);
            if (p != null && !reordered.contains(p)) {
                reordered.add(p);
            }
        }
        // Keep any profiles the client didn't mention (in their original order) so none are ever dropped.
        for (var p : current) {
            if (!reordered.contains(p)) {
                reordered.add(p);
            }
        }
        current.clear();
        current.addAll(reordered);
        saveService.save();
        return Response.ok().build();
    }

    @GET
    @Path("/{serial}/profiles/{name}/settings")
    public ProfileSettingsDto getProfileSettings(@PathParam("serial") String serial, @PathParam("name") String name) {
        return ProfileSettingsDto.from(getProfile(serial, name));
    }

    @PUT
    @Path("/{serial}/profiles/{name}/settings")
    public Response setProfileSettings(@PathParam("serial") String serial, @PathParam("name") String name, ProfileSettingsDto dto) {
        var deviceSave = getDeviceSave(serial);
        var profile = deviceSave.getProfile(name).orElseThrow(() -> new NotFoundException("Profile not found: " + name));
        profile.setFocusBackOnLost(dto.focusBackOnLost());
        profile.setActivateApplications(dto.activateApplications() == null ? new ArrayList<>() : new ArrayList<>(dto.activateApplications()));
        // Exactly one profile may be the main profile.
        if (dto.isMainProfile()) {
            StreamEx.of(deviceSave.getProfiles()).forEach(p -> p.setMainProfile(p == profile));
        } else {
            profile.setMainProfile(false);
        }
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
        eventBus.fire(new AssignmentChangedEvent(serial, Kinds.button, index, commands));
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
        eventBus.fire(new AssignmentChangedEvent(serial, Kinds.dblbutton, index, commands));
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
        eventBus.fire(new AssignmentChangedEvent(serial, Kinds.dial, index, commands));
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
        eventBus.fire(new KnobSettingChangedEvent(serial, index, knob));
        return Response.ok().build();
    }

    // ── Lighting ──────────────────────────────────────────────────────────────

    @GET
    @Path("/{serial}/lighting")
    public LightingConfig getLighting(@PathParam("serial") String serial) {
        var live = deviceHolder.getDevice(serial);
        if (live.isPresent()) {
            return live.get().getSavedLightingConfig();
        }
        // Disconnected: render the current (or first) profile's saved lighting from persistence
        // without creating a profile — a GET must not mutate the saved state.
        var deviceSave = getDeviceSave(serial);
        return deviceSave.getProfile(deviceSave.getCurrentProfileName())
                         .or(() -> deviceSave.getProfiles().stream().findFirst())
                         .map(Profile::lightingConfig)
                         .orElseGet(defaultLightingFor(deviceSave));
    }

    @PUT
    @Path("/{serial}/lighting")
    public Response setLighting(@PathParam("serial") String serial, LightingConfig config) {
        var device = deviceHolder.getDevice(serial).orElseThrow(NotFoundException::new);
        device.setSavedLighting(config);
        saveService.save();
        eventBus.fire(new LightingChangedToDefaultEvent(serial));
        eventBus.fire(new LightingChangedEvent(serial, config));
        return Response.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Default-lighting supplier for a device built from its persisted capability snapshot, so
     * profile/lighting operations work without a live device. Falls back to a solid-color default
     * when capabilities were never back-filled (e.g. a legacy save for a device never reconnected).
     */
    private static java.util.function.Supplier<LightingConfig> defaultLightingFor(DeviceSave deviceSave) {
        var caps = deviceSave.getCapabilities();
        return caps == null
                ? () -> LightingConfig.createAllColor("#0065FF")
                : () -> LightingConfig.defaultLightingConfig(caps);
    }

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

    @PUT
    @Path("/{serial}/profiles/{profile}/controls/{index}")
    public Response setControlAssignments(@PathParam("serial") String serial,
            @PathParam("profile") String profileName,
            @PathParam("index") int index,
            ControlAssignmentsUpdateDto update) {
        var profile = getProfile(serial, profileName);
        var changed = false;

        if (update.analog() != null) {
            profile.setDialData(index, update.analog());
            eventBus.fire(new AssignmentChangedEvent(serial, Kinds.dial, index, update.analog()));
            changed = true;
        }

        if (update.button() != null) {
            profile.setButtonData(index, update.button());
            eventBus.fire(new AssignmentChangedEvent(serial, Kinds.button, index, update.button()));
            changed = true;
        }

        if (update.dblButton() != null) {
            profile.setDblButtonData(index, update.dblButton());
            eventBus.fire(new AssignmentChangedEvent(serial, Kinds.dblbutton, index, update.dblButton()));
            changed = true;
        }

        if (update.knobSetting() != null) {
            var knob = profile.getKnobSettings(index);
            knob.setMinTrim(update.knobSetting().getMinTrim());
            knob.setMaxTrim(update.knobSetting().getMaxTrim());
            knob.setLogarithmic(update.knobSetting().isLogarithmic());
            knob.setOverlayIcon(update.knobSetting().getOverlayIcon());
            knob.setButtonDebounce(update.knobSetting().getButtonDebounce());
            eventBus.fire(new KnobSettingChangedEvent(serial, index, knob));
            changed = true;
        }

        if (changed) {
            saveService.save();
        }

        return Response.ok().build();
    }
}
