package com.getpcpanel.wavelink.command;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.ButtonAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
public final class CommandWaveLinkAddFocusToChannel extends CommandWaveLink implements ButtonAction {
    @Nullable private final String id;
    @Nullable private final String name;

    @JsonCreator
    public CommandWaveLinkAddFocusToChannel(
            @JsonProperty("id") @Nullable String id,
            @JsonProperty("name") @Nullable String name
    ) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String buildLabel() {
        return "Add focus application to " + name;
    }

    @Override
    public void execute() {
        if (StringUtils.isBlank(id)) {
            log.warn("No channel id provided, cannot add focus application");
            return;
        }
        getWaveLinkService().addCurrentToChannel(id);
    }
}
