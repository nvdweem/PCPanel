package com.getpcpanel.integration.volume.command;

import javax.annotation.Nullable;

import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.commands.command.LevelReadable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.integration.volume.VolumeCoordinatorService;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@JsonTypeName("volume.focus")
@CommandMeta(label = "Focused-app volume", category = CommandCategory.audio, kinds = {CommandKind.dial}, icon = "volume", legacyIds = {"com.getpcpanel.commands.command.CommandVolumeFocus"})
public class CommandVolumeFocus extends CommandVolume implements DialAction, LevelReadable {
    private final DialCommandParams dialParams;

    @JsonCreator
    public CommandVolumeFocus(@JsonProperty("dialParams") DialCommandParams dialParams) {
        this.dialParams = dialParams;
    }

    @Override
    public void execute(DialActionParameters context) {
        CdiHelper.getBean(VolumeCoordinatorService.class).setFocusVolume(context.dial().getValue(this, 0, 1), context.device());
    }

    /** The focused app's volume, so "no volume jumps" waits when focus moves to an app at another level. */
    @Override
    @Nullable
    public Float readLevel() {
        return CdiHelper.getBean(VolumeCoordinatorService.class).focusLevel();
    }

    /** The focused app: when focus moves on, the dial meets a level it never set. */
    @Override
    @Nullable
    public Object levelTarget() {
        return CdiHelper.getBean(VolumeCoordinatorService.class).focusApplication();
    }

    @Override
    public String buildLabel() {
        return "";
    }
}

