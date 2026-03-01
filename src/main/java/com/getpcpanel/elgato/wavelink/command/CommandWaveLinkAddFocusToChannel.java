package com.getpcpanel.elgato.wavelink.command;

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
public class CommandWaveLinkAddFocusToChannel extends CommandWaveLink implements ButtonAction {
    @Nullable private final String channelId;
    @Nullable private final String channelName;

    @JsonCreator
    public CommandWaveLinkAddFocusToChannel(
            @JsonProperty("id") @Nullable String channelId,
            @JsonProperty("name") @Nullable String channelName
    ) {
        this.channelId = channelId;
        this.channelName = channelName;
    }

    @Override
    public String buildLabel() {
        return "Add focus application to " + channelName;
    }

    @Override
    public void execute() {
        if (StringUtils.isBlank(channelId)) {
            log.warn("No channel id provided, cannot add focus application");
            return;
        }
        getWaveLinkService().addCurrentToChannel(channelId);
    }
}
