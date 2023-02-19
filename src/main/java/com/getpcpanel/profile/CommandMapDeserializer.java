package com.getpcpanel.profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandConverter;

import one.util.streamex.EntryStream;
import one.util.streamex.IntStreamEx;

public class CommandMapDeserializer extends JsonDeserializer<Map<Integer, Commands>> {
    @Override
    public Map<Integer, Commands> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            return readOldFormat(p);
        }
        var tree = p.<ObjectNode>readValueAsTree();

        try {
            var newType = new TypeReference<Map<Integer, Commands>>() {
            };
            return ctxt.readTreeAsValue(tree, new ObjectMapper().constructType(newType));
        } catch (Exception e) {
            // Read old format
            var oldType = new TypeReference<Map<Integer, Command>>() {
            };
            var result = ctxt.<Map<Integer, Command>>readTreeAsValue(tree, new ObjectMapper().constructType(oldType));
            return convertMapToMapWithCommands(result);
        }
    }

    private @Nonnull Map<Integer, Commands> convertMapToMapWithCommands(@Nonnull Map<Integer, Command> result) {
        return EntryStream.of(result).mapValues(cmd -> new Commands(List.of(cmd))).toMap();
    }

    private @Nonnull Map<Integer, Commands> readOldFormat(@Nonnull JsonParser p) throws IOException {
        var result = new HashMap<Integer, Commands>();
        var arr = p.getCodec().readTree(p);
        IntStreamEx.range(arr.size())
                   .mapToEntry(t -> t, arr::get)
                   .mapValues(tn -> IntStreamEx.range(tn.size()).mapToObj(tn::get).select(ValueNode.class).map(ValueNode::asText).toArray(String[]::new))
                   .mapValues(CommandConverter::convert)
                   .nonNullValues()
                   .mapValues(cmd -> new Commands(List.of(cmd)))
                   .into(result);
        return result;
    }
}
