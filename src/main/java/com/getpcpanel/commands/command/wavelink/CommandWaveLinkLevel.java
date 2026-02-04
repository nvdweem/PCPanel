package com.getpcpanel.commands.command.wavelink;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.wavelink.WaveLinkService;

import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import io.reactivex.annotations.Nullable;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
// @RequiredArgsConstructor
public class CommandWaveLinkLevel extends CommandWaveLink implements DialAction {
    private final WaveLinkCommand commandType;
    @Nullable private final String id1;
    @Nullable private final String id2;

    @JsonCreator
    public CommandWaveLinkLevel(
            @JsonProperty("commandType") WaveLinkCommand commandType,
            @JsonProperty("id1") @Nullable String id1,
            @JsonProperty("id2") @Nullable String id2) {
        this.commandType = commandType;
        this.id1 = id1;
        this.id2 = id2;
    }

    @Override
    public String buildLabel() {
        return "Set " + commandType;
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = MainFX.getBean(WaveLinkService.class);
        if (!service.isConnected()) {
            log.warn("Not sending command, wavelink not connected");
            return;
        }
        if (StringUtils.isBlank(id1)) {
            log.warn("No id specified");
            return;
        }
        var value = (double) context.dial().getValue(this, 0, 1);
        switch (commandType) {
            case Input -> service.setInputLevel(id1, WaveLinkControlAction.OutputVolume, value);
            case Channel -> service.setChannelLevel(id1, value);
            case Mix -> service.setChannelLevel(id1, id2, value);
            case Output -> service.setOutputLevel(id1, value);
        }
    }

    @Override
    @Nullable
    public DialCommandParams getDialParams() {
        return null;
    }

    public enum WaveLinkCommand {
        Input, Channel, Mix, Output
    }
}
