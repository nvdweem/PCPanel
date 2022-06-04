package com.getpcpanel.profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandConverter;

import one.util.streamex.IntStreamEx;

public class CommandMapDeserializer extends JsonDeserializer<Map<Integer, Command>> {
    @Override
    public Map<Integer, Command> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            return readOldFormat(p);
        }
        var type = new TypeReference<Map<Integer, Command>>() {
        };
        return p.readValueAs(type);
    }

    private Map<Integer, Command> readOldFormat(JsonParser p) throws IOException {
        var result = new HashMap<Integer, Command>();
        var arr = p.getCodec().readTree(p);
        IntStreamEx.range(arr.size())
                   .mapToEntry(t -> t, arr::get)
                   .mapValues(tn -> IntStreamEx.range(tn.size()).mapToObj(tn::get).select(ValueNode.class).map(ValueNode::asText).toArray(String[]::new))
                   .mapValues(CommandConverter::convert)
                   .nonNullValues()
                   .into(result);
        return result;
    }
}
