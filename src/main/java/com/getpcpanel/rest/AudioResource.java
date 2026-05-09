package com.getpcpanel.rest;

import java.util.Collection;
import java.util.List;

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

import one.util.streamex.StreamEx;

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
}
