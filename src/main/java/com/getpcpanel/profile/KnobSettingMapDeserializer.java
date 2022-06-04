package com.getpcpanel.profile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class KnobSettingMapDeserializer extends JsonDeserializer<Map<Integer, KnobSetting>> {
    @Override
    public Map<Integer, KnobSetting> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.START_ARRAY) {
            return readOldFormat(p);
        }
        return null;
    }

    private Map<Integer, KnobSetting> readOldFormat(JsonParser p) throws IOException {
        var result = new HashMap<Integer, KnobSetting>();
        while (p.nextToken() == JsonToken.START_OBJECT) {
            result.put(result.size(), p.readValueAs(KnobSetting.class));
        }
        return result;
    }
}
