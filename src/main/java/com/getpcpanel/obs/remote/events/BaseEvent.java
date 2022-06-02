package com.getpcpanel.obs.remote.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class BaseEvent {
    private EventType eventType;

    public EventType getEventType() {
        return eventType;
    }

    @JsonProperty("update-type")
    public void setUpdateType(String updateType) {
        log.debug(updateType);
        eventType = EventType.valueOf(updateType);
    }
}
