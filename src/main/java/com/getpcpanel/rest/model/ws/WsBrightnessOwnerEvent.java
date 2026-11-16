package com.getpcpanel.rest.model.ws;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.integration.device.dto.BrightnessOwner;

/** What now sets the device's brightness; null when its saved brightness applies. */
@JsonTypeName("brightness_owner")
public record WsBrightnessOwnerEvent(String serial, @Nullable BrightnessOwner owner) implements WsEvent {
}
