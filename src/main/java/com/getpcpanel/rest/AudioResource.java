package com.getpcpanel.rest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.getpcpanel.cpp.AudioDevice;
import com.getpcpanel.cpp.AudioSession;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.MuteType;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Path("/api/audio")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AudioResource {
    @Inject ISndCtrl sndCtrl;

    @GET
    @Path("/devices")
    public Collection<AudioDevice> listAudioDevices() {
        return sndCtrl.devices();
    }

    @GET
    @Path("/devices/output")
    public List<AudioDevice> listOutputDevices() {
        return StreamEx.of(sndCtrl.devices()).filter(AudioDevice::isOutput).toList();
    }

    @GET
    @Path("/devices/input")
    public List<AudioDevice> listInputDevices() {
        return StreamEx.of(sndCtrl.devices()).filter(AudioDevice::isInput).toList();
    }

    @GET
    @Path("/sessions")
    public Collection<AudioSession> listAudioSessions() {
        return sndCtrl.getAllSessions();
    }

    @GET
    @Path("/applications")
    public List<ISndCtrl.RunningApplication> listRunningApplications() {
        return sndCtrl.getRunningApplications();
    }

    /**
     * Diagnostic probe that exercises the volume/mute <em>write</em> path on the default output device.
     * The read endpoints above (devices/sessions/applications) only cover enumeration, so they cannot
     * catch a native-image registration gap on the settable path: an unregistered JNA pointer type there
     * throws on the first volume/mute write while {@code /api/audio/devices} keeps returning 200 (#105).
     * This re-applies the device's <em>current</em> volume and mute state (a no-op for the user) but
     * still routes through {@code setDeviceVolume}/{@code muteDevice} →
     * {@code CoreAudioWrapper.isSettable} → {@code new ByteByReference()}, so the CI smoke test reproduces
     * that class of bug. Any missing registration makes this 500; a runner with no default device returns
     * {@code exercised:false} (200), which the smoke treats leniently.
     */
    @GET
    @Path("/selftest")
    public Map<String, Object> selfTest() {
        var result = new LinkedHashMap<String, Object>();
        // Prefer the default output device, but fall back to any output device (then any device) so the
        // probe still exercises the settable path on a runner where no device is flagged "default".
        var devices = sndCtrl.devices();
        var defaultId = sndCtrl.defaultPlayer();
        var device = StreamEx.of(devices).findFirst(d -> d.id().equals(defaultId))
                .or(() -> StreamEx.of(devices).findFirst(AudioDevice::isOutput))
                .or(() -> StreamEx.of(devices).findFirst())
                .orElse(null);
        if (device == null) {
            result.put("exercised", false);
            result.put("reason", "no audio devices");
            return result;
        }
        // Re-apply the current state: same volume, same mute — exercises the settable path without
        // changing anything audible.
        sndCtrl.setDeviceVolume(device.id(), device.volume());
        sndCtrl.muteDevice(device.id(), device.muted() ? MuteType.mute : MuteType.unmute);
        log.debug("Audio self-test exercised the settable path on '{}'", device.name());
        result.put("exercised", true);
        result.put("device", device.name());
        return result;
    }
}
