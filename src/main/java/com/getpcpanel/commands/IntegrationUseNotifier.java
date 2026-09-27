package com.getpcpanel.commands;

import java.util.List;

import com.getpcpanel.commands.command.Command;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import one.util.streamex.StreamEx;

/**
 * Tells each {@link IntegrationConnection} that a control is about to run one of its commands. Commands
 * nested in another (a stepped-switch dial's bands) count as used too, whichever band the input lands in.
 */
@ApplicationScoped
public class IntegrationUseNotifier {
    private final List<IntegrationConnection> connections;

    @Inject
    public IntegrationUseNotifier(@All List<IntegrationConnection> connections) {
        this.connections = List.copyOf(connections);
    }

    public void onUsed(Commands commands) {
        var used = allCommands(commands).toList();
        for (var connection : connections) {
            if (used.stream().anyMatch(connection::owns)) {
                connection.onUsed();
            }
        }
    }

    private static StreamEx<Command> allCommands(Commands commands) {
        return StreamEx.of(commands.getCommands())
                       .nonNull()
                       .flatMap(IntegrationUseNotifier::withNested);
    }

    private static StreamEx<Command> withNested(Command command) {
        if (command instanceof NestedCommands nested) {
            return StreamEx.of(nested.nestedCommands())
                           .nonNull()
                           .flatMap(IntegrationUseNotifier::allCommands)
                           .prepend(command);
        }
        return StreamEx.of(command);
    }
}
