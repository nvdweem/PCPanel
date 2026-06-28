package com.getpcpanel.keyboard.command;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.ButtonAction;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.commands.KeyMacro;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

/**
 * Button action that synthesises keyboard input. Two flavours, selected by {@link #type}:
 * <ul>
 *     <li>{@link KeystrokeType#KEY} — presses a single key combination such as {@code ctrl+shift+A}
 *     (the cross-platform "{@code modifier+modifier+key}" format parsed by {@link KeyMacro}).</li>
 *     <li>{@link KeystrokeType#TEXT} — types an arbitrary string character-by-character.</li>
 * </ul>
 * Profiles saved before this field existed deserialize with a {@code null} {@code type}, which
 * defaults to {@link KeystrokeType#KEY} so legacy {@code keystroke} values keep working.
 */
@Getter
@Log4j2
@ToString(callSuper = true)
@JsonTypeName("com.getpcpanel.commands.command.CommandKeystroke")
public class CommandKeystroke extends Command implements ButtonAction {
    public enum KeystrokeType {
        /** Single key combination (modifiers + one key). */
        KEY,
        /** Arbitrary text typed out character-by-character. */
        TEXT
    }

    private final KeystrokeType type;
    private final String keystroke;
    private final String text;

    @JsonCreator
    public CommandKeystroke(@JsonProperty("type") KeystrokeType type,
            @JsonProperty("keystroke") String keystroke,
            @JsonProperty("text") String text) {
        this.type = type == null ? KeystrokeType.KEY : type;
        this.keystroke = keystroke;
        this.text = text;
    }

    @Override
    public void execute() {
        if (type == KeystrokeType.TEXT) {
            KeyMacro.typeText(text);
        } else {
            KeyMacro.executeKeyStroke(keystroke);
        }
    }

    @Override
    public String buildLabel() {
        return type == KeystrokeType.TEXT ? StringUtils.defaultString(text) : StringUtils.defaultString(keystroke);
    }
}
