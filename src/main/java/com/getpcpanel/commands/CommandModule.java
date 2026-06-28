package com.getpcpanel.commands;

import java.util.List;

import com.getpcpanel.commands.command.Command;

/**
 * A feature module's contribution of assignable command types.
 *
 * <p>Each module is an {@code @ApplicationScoped} bean that lives in <em>its own feature package</em>
 * and lists only the {@link Command} subclasses it owns. {@link CommandSubtypeRegistrar} collects every
 * {@code CommandModule} via CDI {@code @All} and registers the classes with Jackson (each class carries
 * its own {@code @JsonTypeName}). Adding a command — or an entire new feature/plugin — therefore never
 * touches anything outside the feature's package: drop the command class in, list it here, done.
 */
public interface CommandModule {
    List<Class<? extends Command>> commandTypes();
}
