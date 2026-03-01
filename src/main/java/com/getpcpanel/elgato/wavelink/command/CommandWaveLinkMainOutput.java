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
public class CommandWaveLinkMainOutput extends CommandWaveLink implements ButtonAction {
    @Nullable private final String id;
    @Nullable private final String name;

    @JsonCreator
    public CommandWaveLinkMainOutput(
            @JsonProperty("id") @Nullable String id,
            @JsonProperty("name") @Nullable String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String buildLabel() {
        return "Set main output to " + name;
    }

    @Override
    public void execute() {
        var service = getWaveLinkService();
        if (!service.isConnected()) {
            log.warn("Not sending command, wavelink not connected");
            return;
        }
        if (StringUtils.isBlank(id)) {
            log.warn("No id specified");
            return;
        }

        service.setMainOutput(id);
    }
}
