package com.getpcpanel.wavelink.command;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.command.DialAction;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("wavelink.change-level")
@CommandMeta(label = "Wave Link — level", category = CommandCategory.integration, kinds = {CommandKind.dial}, integration = "wavelink", icon = "sliders", legacyIds = {"com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel"})
public final class CommandWaveLinkChangeLevel extends CommandWaveLinkChange implements DialAction {
    @Nullable private final DialCommandParams dialParams;

    @JsonCreator
    public CommandWaveLinkChangeLevel(
            @JsonProperty("commandType") WaveLinkCommandTarget commandType,
            @JsonProperty("id1") @Nullable String id1,
            @JsonProperty("id2") @Nullable String id2,
            @JsonProperty("dialParams") @Nullable DialCommandParams dialParams) {
        super(commandType, id1, id2);
        this.dialParams = dialParams;
    }

    @Override
    public String buildLabel() {
        var name = targetName();
        return name != null ? name + " — Wave Link" : "Set " + getCommandType();
    }

    @Override
    public void execute(DialActionParameters context) {
        var service = getWaveLinkService();
        if (!service.isConnected()) {
            log.warn("Not sending command, wavelink not connected");
            return;
        }
        if (StringUtils.isBlank(getId1())) {
            log.warn("No id specified");
            return;
        }
        var value = (double) context.dial().getValue(this, 0, 1);
        switch (getCommandType()) {
            case Input -> service.setInputLevel(getId1(), value);
            case Channel -> service.setChannelLevel(getId1(), value);
            case Mix -> service.setChannelLevel(getId1(), getId2(), value);
            case Output -> service.setOutputLevel(getId1(), value);
        }
    }
}
