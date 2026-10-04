package com.getpcpanel.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class HistoryChangesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode save(String profile) throws Exception {
        return MAPPER.readTree("""
                {"dblClickInterval": 500,
                 "devices": {"S1": {"displayName": "Desk", "currentProfileName": "A",
                   "capabilities": {"analogInputs": [{"label": "K1"}, {"label": "K2"}, {"label": "K3"}]},
                   "profiles": [%s]}}}""".formatted(profile));
    }

    @Test
    void namesTheControlAndProfile() throws Exception {
        var before = save("""
                {"name": "A", "dialData": {"2": {"commands": []}}, "knobSettings": {}, "lightingConfig": {"mode": 1}}""");
        var after = save("""
                {"name": "A", "dialData": {"2": {"commands": [{"x": 1}]}}, "buttonData": {"2": {}}, "knobSettings": {}, "lightingConfig": {"mode": 1}}""");
        assertEquals(List.of("K3 actions · A"), HistoryChanges.describe(before, after));
    }

    @Test
    void lightingAndSettings() throws Exception {
        var before = save("""
                {"name": "A", "lightingConfig": {"mode": 1}}""");
        var after = (com.fasterxml.jackson.databind.node.ObjectNode) save("""
                {"name": "A", "lightingConfig": {"mode": 2}}""");
        after.put("dblClickInterval", 300);
        assertEquals(List.of("Lighting · A", "Settings"), HistoryChanges.describe(before, after));
    }

    @Test
    void controlSettingsAndProfiles() throws Exception {
        var before = save("""
                {"name": "A", "knobSettings": {"0": {"minTrim": 0}}}""");
        var after = save("""
                {"name": "A", "knobSettings": {"0": {"minTrim": 5}}}, {"name": "B"}""");
        assertEquals(List.of("K1 settings · A", "Profiles of Desk"), HistoryChanges.describe(before, after));
    }
}
