package com.getpcpanel.rest.model.dto;

import com.getpcpanel.rest.EventBroadcaster.AssignmentChangedEvent.Kinds;

public record CommandType(
        String name,
        String command,
        CommandCategory category,
        Kinds kind
) {

    public enum CommandCategory {
        standard, voicemeeter, obs, wavelink
    }
}
