package com.getpcpanel.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.hid.DeviceCommunicationHandler.ButtonPressEvent;
import com.getpcpanel.hid.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.hid.DeviceHolder.DeviceFullyConnectedEvent;
import com.getpcpanel.hid.DeviceScanner.DeviceDisconnectedEvent;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.rest.ProVisualColorsService.ProVisualColors;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;
import com.getpcpanel.rest.model.dto.ProfileSnapshotDto;
import com.getpcpanel.rest.model.ws.WsAssignmentChangedEvent;
import com.getpcpanel.rest.model.ws.WsButtonEvent;
import com.getpcpanel.rest.model.ws.WsDeviceConnectedEvent;
import com.getpcpanel.rest.model.ws.WsDeviceDisconnectedEvent;
import com.getpcpanel.rest.model.ws.WsDeviceRenamedEvent;
import com.getpcpanel.rest.model.ws.WsKnobEvent;
import com.getpcpanel.rest.model.ws.WsLightingChangedEvent;
import com.getpcpanel.rest.model.ws.WsProfileSwitchedEvent;
import com.getpcpanel.rest.model.ws.WsVisualColorsChangedEvent;
import com.getpcpanel.util.AppShutdownState;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class EventBroadcaster {
    @Inject ObjectMapper objectMapper;
    @Inject SaveService saveService;
    @Inject DeviceHolder deviceHolder;
    @Inject ProVisualColorsService proVisualColorsService;

    private boolean shouldSkipBroadcast() {
        return AppShutdownState.isShuttingDown();
    }

    private void broadcast(Object event) {
        if (shouldSkipBroadcast())
            return;
        EventWebSocket.broadcast(event, objectMapper);
    }

    // ── Existing operational events ────────────────────────────────────────────

    public void onDeviceConnected(@Observes DeviceFullyConnectedEvent event) {
        var serial = event.device().getSerialNumber();
        var save = saveService.get().getDeviceSave(serial);
        if (save == null) {
            log.debug("Skipping device_connected broadcast for {} because no device save exists", serial);
            return;
        }

        var snapshot = DeviceSnapshotDto.from(event.device(), save, proVisualColorsService);
        broadcast(new WsDeviceConnectedEvent(snapshot));
    }

    public void onDeviceDisconnected(@Observes DeviceDisconnectedEvent event) {
        broadcast(new WsDeviceDisconnectedEvent(event.serialNum()));
    }

    public void onKnobRotate(@Observes KnobRotateEvent event) {
        broadcast(new WsKnobEvent(event.serialNum(), event.knob(), event.value()));
    }

    public void onButtonPress(@Observes ButtonPressEvent event) {
        broadcast(new WsButtonEvent(event.serialNum(), event.button(), event.pressed()));
    }

    // ── Mutation patch events ──────────────────────────────────────────────────

    public void onDeviceRenamed(@Observes DeviceRenamedEvent event) {
        broadcast(new WsDeviceRenamedEvent(event.serial(), event.displayName()));
    }

    public void onProfileSwitched(@Observes ProfileSwitchedEvent event) {
        var colors = colorsFor(event.serial());
        broadcast(new WsProfileSwitchedEvent(event.serial(), event.profileName(), event.profileSnapshot(), colors.dialColors(), colors.sliderLabelColors(), colors.sliderColors(), colors.logoColor()));
    }

    public void onLightingChanged(@Observes LightingChangedEvent event) {
        var colors = colorsFor(event.serial());
        broadcast(new WsLightingChangedEvent(event.serial(), event.lightingConfig(), colors.dialColors(), colors.sliderLabelColors(), colors.sliderColors(), colors.logoColor()));
    }

    public void onVisualColorsChanged(@Observes VisualColorsChangedEvent event) {
        var colors = colorsFor(event.serial());
        broadcast(new WsVisualColorsChangedEvent(event.serial(), colors.dialColors(), colors.sliderLabelColors(), colors.sliderColors(), colors.logoColor()));
    }

    public void onAssignmentChanged(@Observes AssignmentChangedEvent event) {
        broadcast(new WsAssignmentChangedEvent(event.serial(), event.kind(), event.index(), event.commands()));
    }

    // ── CDI mutation events (fired by DeviceResource) ─────────────────────────

    public record DeviceRenamedEvent(String serial, String displayName) {
    }

    public record ProfileSwitchedEvent(String serial, String profileName, ProfileSnapshotDto profileSnapshot) {
    }

    public record LightingChangedEvent(String serial, LightingConfig lightingConfig) {
    }

    public record VisualColorsChangedEvent(String serial) {
    }

    public record AssignmentChangedEvent(String serial, Kinds kind, int index, Commands commands) {
        public enum Kinds {
            dial, button, dblbutton
        }
    }

    private ProVisualColors colorsFor(String serial) {
        return deviceHolder.getDevice(serial)
                           .map(proVisualColorsService::resolve)
                           .orElse(ProVisualColors.empty());
    }
}
