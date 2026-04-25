package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
@JsonSubTypes({
        @Type(value = WsAssignmentChangedEvent.class, name = "assignment_changed"),
        @Type(value = WsButtonEvent.class, name = "button_press"),
        @Type(value = WsDeviceConnectedEvent.class, name = "device_connected"),
        @Type(value = WsDeviceDisconnectedEvent.class, name = "device_disconnected"),
        @Type(value = WsDeviceRenamedEvent.class, name = "device_renamed"),
        @Type(value = WsKnobEvent.class, name = "knob_rotate"),
        @Type(value = WsLightingChangedEvent.class, name = "lighting_changed"),
        @Type(value = WsProfileSwitchedEvent.class, name = "profile_switched"),
        @Type(value = WsVisualColorsChangedEvent.class, name = "visual_colors_changed"),
        @Type(value = DeviceSnapshotDto.class, name = "device_snapshot")
})
public interface WsEvent {
}
