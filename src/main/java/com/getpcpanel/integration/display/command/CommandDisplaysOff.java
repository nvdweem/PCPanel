package com.getpcpanel.integration.display.command;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Button action for the displays. With no displays chosen it puts them all to sleep, and moving the mouse or pressing
 * a key wakes them. With displays chosen it switches those monitors off over DDC/CI, and the next press turns them
 * back on.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("display.off")
@CommandMeta(label = "Turn displays off", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "monitor")
public class CommandDisplaysOff extends Command implements ButtonAction {
    /** {@link DisplayPower.DisplayInfo#id Ids} of the chosen monitors; empty means all displays. */
    private final List<String> displays;

    @JsonCreator
    public CommandDisplaysOff(@JsonProperty("displays") @Nullable List<String> displays) {
        this.displays = displays == null ? List.of() : displays.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public void execute() {
        var power = CdiHelper.getBean(DisplayPower.class);
        if (displays.isEmpty()) {
            power.turnOff();
        } else {
            power.toggle(displays);
        }
    }

    @Override
    public String buildLabel() {
        return displays.isEmpty() ? "Displays off" : "Displays off/on (" + displays.size() + ")";
    }
}
