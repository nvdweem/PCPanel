package com.getpcpanel.homeassistant.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;

import lombok.Getter;
import lombok.ToString;

/**
 * Button action that performs a Home Assistant action. The user pastes the action YAML (from HA's
 * Developer Tools → Actions); it is parsed and sent to the REST API as-is. Home Assistant Jinja
 * templates inside the YAML are preserved and rendered server-side by Home Assistant.
 */
@Getter
@ToString(callSuper = true)
public class CommandHomeAssistantAction extends CommandHomeAssistant implements ButtonAction {
    private final String action;
    @Nullable private final String overlayText;

    @JsonCreator
    public CommandHomeAssistantAction(
            @JsonProperty("server") @Nullable String server,
            @JsonProperty("action") String action,
            @JsonProperty("overlayText") @Nullable String overlayText) {
        super(server);
        this.action = action;
        this.overlayText = overlayText;
    }

    @Override
    public void execute() {
        if (StringUtils.isBlank(action)) {
            return;
        }
        service().callAction(server, action);
    }

    @Override
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public @Nullable String getOverlayText() {
        return overlayText;
    }

    @Override
    public String buildLabel() {
        return "HA: " + StringUtils.substringBefore(StringUtils.defaultString(action).strip(), "\n");
    }
}
