package com.getpcpanel.commands;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;

import one.util.streamex.StreamEx;

public record Commands(@Nonnull List<Command> commands) {
    public static final Commands EMPTY = new Commands(List.of());

    public Commands {
        commands = StreamEx.of(commands).remove(CommandNoOp.class::isInstance).toImmutableList();
    }

    public static @Nonnull List<Command> cmds(@Nullable Commands buttonData) {
        if (buttonData != null) {
            return buttonData.commands();
        }
        return List.of();
    }

    public <T extends Command> Optional<T> getCommand(Class<T> cmd) {
        return StreamEx.of(commands).select(cmd).findFirst();
    }

    public static boolean hasCommands(@Nullable Commands cmds) {
        return cmds != null && !cmds.commands.isEmpty();
    }
}
