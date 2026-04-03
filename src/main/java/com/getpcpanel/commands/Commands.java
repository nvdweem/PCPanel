package com.getpcpanel.commands;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;

import lombok.Data;
import one.util.streamex.StreamEx;

@Data
public class Commands {
    public static final Commands EMPTY = new Commands(List.of(), CommandsType.allAtOnce);
    private final @Nonnull List<Command> commands;
    private final @Nonnull CommandsType type;
    @JsonIgnore private int sequenceIdx = -1;

    public static @Nonnull List<Command> cmds(@Nullable Commands buttonData) {
        if (buttonData != null) {
            return buttonData.getCommands();
        }
        return List.of();
    }

    public Commands(@Nonnull List<Command> commands, @Nullable CommandsType type) {
        this.commands = StreamEx.of(commands).remove(CommandNoOp.class::isInstance).toImmutableList();
        this.type = type != null ? type : CommandsType.allAtOnce;
    }

    public <T extends Command> Optional<T> getCommand(Class<T> cmd) {
        return StreamEx.of(commands).select(cmd).findFirst();
    }

    public static boolean hasCommands(@Nullable Commands cmds) {
        return cmds != null && !cmds.commands.isEmpty();
    }
}
