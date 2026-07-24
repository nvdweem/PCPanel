package com.getpcpanel.integration.clipboard.command;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandCategory;
import com.getpcpanel.commands.meta.CommandKind;
import com.getpcpanel.commands.meta.CommandMeta;
import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Button action that writes a configured piece of text to the OS clipboard, via the platform
 * {@link ClipboardWriter}. Useful as a paste-a-snippet macro key.
 */
@Getter
@ToString(callSuper = true)
@JsonTypeName("clipboard.set")
@CommandMeta(label = "Set clipboard", category = CommandCategory.system, kinds = {CommandKind.button}, icon = "clipboard")
public class CommandSetClipboard extends Command implements ButtonAction {
    private final String text;

    @JsonCreator
    public CommandSetClipboard(@JsonProperty("text") String text) {
        this.text = text;
    }

    @Override
    public void execute() {
        CdiHelper.getBean(ClipboardWriter.class).setText(text);
    }

    @Override
    public String buildLabel() {
        return StringUtils.abbreviate(StringUtils.defaultString(text), 20);
    }
}
