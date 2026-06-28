package com.getpcpanel.commands;

import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.meta.CommandMeta;

import io.quarkus.arc.All;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.log4j.Log4j2;

/**
 * Registers every feature module's command classes as Jackson polymorphic subtypes of
 * {@link Command}. The subtypes are discovered entirely through the {@link CommandModule} CDI SPI
 * ({@code @All}), so no central list of command classes exists anywhere — a new command/plugin only
 * declares itself in its own package. Jackson reads each class's {@code @JsonTypeName} for the
 * (nice, current) persisted id.
 *
 * <p><b>Backwards compatibility.</b> A command's {@code @CommandMeta.legacyIds} list the {@code _type}
 * strings older {@code profiles.json} files may contain (their previous fully-qualified class names).
 * These are <em>not</em> registered as subtypes (that would risk them being chosen for serialization);
 * instead a {@link DeserializationProblemHandler} maps an unknown legacy id back to its command on
 * read only. So existing saves keep loading, and re-saving rewrites them with the nice current id —
 * a transparent one-way conversion.
 */
@Log4j2
@Singleton
public class CommandSubtypeRegistrar implements ObjectMapperCustomizer {
    @Inject
    @All
    List<CommandModule> modules;

    @Override
    public void customize(ObjectMapper mapper) {
        var classes = modules.stream().flatMap(m -> m.commandTypes().stream()).distinct().toList();
        var legacy = new HashMap<String, Class<?>>();
        for (var clazz : classes) {
            mapper.registerSubtypes(clazz); // current id via @JsonTypeName
            var meta = clazz.getAnnotation(CommandMeta.class);
            if (meta != null) {
                for (var id : meta.legacyIds()) {
                    var prev = legacy.put(id, clazz);
                    if (prev != null && prev != clazz) {
                        throw new IllegalStateException("Legacy command id '" + id + "' maps to both " + prev + " and " + clazz);
                    }
                }
            }
        }
        if (!legacy.isEmpty()) {
            // Resolve a legacy _type id (a command's old FQCN) to its current class on read only.
            mapper.addHandler(new DeserializationProblemHandler() {
                @Override
                public JavaType handleUnknownTypeId(DeserializationContext ctxt, JavaType baseType, String subTypeId, TypeIdResolver idResolver, String failureMsg) {
                    if (baseType.isTypeOrSubTypeOf(Command.class)) {
                        var clazz = legacy.get(subTypeId);
                        if (clazz != null) {
                            return ctxt.getTypeFactory().constructType(clazz);
                        }
                    }
                    return null; // not a known legacy id — let Jackson report the failure
                }
            });
        }
        log.debug("Registered {} command subtypes ({} legacy aliases) from {} module(s)", classes.size(), legacy.size(), modules.size());
    }
}
