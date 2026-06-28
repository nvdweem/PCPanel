package com.getpcpanel.integration.homeassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the parsing of the pasted Home Assistant action YAML into the {@code domain.service} + flat
 * body the REST API expects: the modern {@code action:} key, the legacy {@code service:} key, the
 * merge of {@code target}/{@code data}, the single-step-of-a-list case, and the invalid-input cases.
 */
@DisplayName("HaActionYaml parsing")
class HaActionYamlTest {
    @Test
    @DisplayName("action + target + data → domain/service and a flat merged body")
    void parsesActionTargetData() {
        var parsed = HaActionYaml.parse("""
                action: light.turn_on
                target:
                  entity_id: light.living_room
                data:
                  brightness: 191
                """);
        assertEquals("light", parsed.domain());
        assertEquals("turn_on", parsed.service());
        assertEquals("light.living_room", parsed.data().get("entity_id"));
        assertEquals(191, parsed.data().get("brightness"));
    }

    @Test
    @DisplayName("legacy 'service:' key is accepted")
    void parsesLegacyServiceKey() {
        var parsed = HaActionYaml.parse("service: switch.toggle\nentity_id: switch.fan");
        assertEquals("switch", parsed.domain());
        assertEquals("toggle", parsed.service());
        assertEquals("switch.fan", parsed.data().get("entity_id"));
    }

    @Test
    @DisplayName("a list of action steps uses the first step")
    void parsesFirstOfList() {
        var parsed = HaActionYaml.parse("- action: light.turn_off\n  target:\n    entity_id: light.x\n- action: light.turn_on");
        assertEquals("light", parsed.domain());
        assertEquals("turn_off", parsed.service());
    }

    @Test
    @DisplayName("blank, malformed, or action-less YAML returns null")
    void invalidReturnsNull() {
        assertNull(HaActionYaml.parse(""));
        assertNull(HaActionYaml.parse("   "));
        assertNull(HaActionYaml.parse("data:\n  brightness: 10"));   // no action/service
        assertNull(HaActionYaml.parse("action: noseparator"));       // not domain.service
        assertNull(HaActionYaml.parse(":\n  - ["));                  // not valid YAML
    }
}
