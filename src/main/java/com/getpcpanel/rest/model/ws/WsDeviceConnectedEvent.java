package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.rest.model.dto.DeviceSnapshotDto;

@JsonTypeName("device_connected")
public record WsDeviceConnectedEvent(
        DeviceSnapshotDto deviceSnapshot
) implements WsEvent {
}
