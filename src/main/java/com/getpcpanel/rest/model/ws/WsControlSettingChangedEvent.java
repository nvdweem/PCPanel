package com.getpcpanel.rest.model.ws;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.profile.dto.KnobSetting;

@JsonTypeName("control_setting_changed")
public record WsControlSettingChangedEvent(String serial, String profile, int index, KnobSetting settings) implements WsEvent {
}
