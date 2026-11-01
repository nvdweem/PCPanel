package com.getpcpanel.commands.command;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import lombok.ToString;

/**
 * A saved command whose {@code _type} this build does not know, such as one added by a newer version. It does
 * nothing, and it is written back exactly as it was read, {@code _type} included, so going back to an older build
 * neither fails to read the configuration nor drops the command. {@link com.getpcpanel.commands.CommandSubtypeRegistrar}
 * reads every unknown id as this.
 */
@ToString(callSuper = true)
@JsonIgnoreProperties(value = {}, allowGetters = false) // replaces Command's: this keeps its _type
public final class CommandUnknown extends Command implements ButtonAction, DialAction, JsonSerializable {
    private final Map<String, JsonNode> properties = new LinkedHashMap<>();

    @JsonAnySetter
    void set(String name, JsonNode value) {
        properties.put(name, value);
    }

    /** The {@code _type} it was saved with. */
    public String type() {
        var type = properties.get(TYPE_PROPERTY);
        return type == null ? "" : type.asText();
    }

    @Override
    public void serialize(JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        for (var property : properties.entrySet()) {
            gen.writeFieldName(property.getKey());
            gen.writeTree(property.getValue());
        }
        gen.writeEndObject();
    }

    /** Written as read: the saved {@code _type} is one of its properties, so no type id is added. */
    @Override
    public void serializeWithType(JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
        serialize(gen, serializers);
    }

    @Override
    public void execute() {
    }

    @Override
    public void execute(DialActionParameters context) {
    }

    @Override
    public Runnable toRunnable() {
        return ButtonAction.super.toRunnable();
    }

    @Override
    public boolean hasOverlay() {
        return false;
    }

    @Override
    public DialCommandParams getDialParams() {
        return DialCommandParams.DEFAULT;
    }

    @Override
    public String buildLabel() {
        return "Unknown action (" + type() + ")";
    }
}
