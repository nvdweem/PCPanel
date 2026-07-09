package com.getpcpanel.integration.homeassistant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getpcpanel.util.ValueInterpolator;

/**
 * The dial half of {@link com.getpcpanel.integration.homeassistant.command.CommandHomeAssistantValue}:
 * the normalised 0..1 position is translated to a number ({@link ValueInterpolator#translate} — linear
 * min/max or an exp4j formula with variable {@code x}) and substituted for the {@code {{ value }}}
 * token in the pasted action YAML before it is parsed and sent. The tests run that exact pipeline
 * (translate → interpolate → {@link HaActionYaml#parse}) without a server.
 */
@DisplayName("Home Assistant dial value mapping")
class HomeAssistantValueMappingTest {
    @Test
    @DisplayName("linear min/max: x=0.5 between 10 and 30 is 20")
    void linearMinMax() {
        assertEquals(20d, ValueInterpolator.translate(0.5, 10d, 30d, null));
        assertEquals(10d, ValueInterpolator.translate(0, 10d, 30d, null));
        assertEquals(30d, ValueInterpolator.translate(1, 10d, 30d, null));
    }

    @Test
    @DisplayName("min/max default to 0..100 when unset")
    void linearDefaults() {
        assertEquals(0d, ValueInterpolator.translate(0, null, null, null));
        assertEquals(50d, ValueInterpolator.translate(0.5, null, null, null));
        assertEquals(100d, ValueInterpolator.translate(1, null, null, null));
    }

    @Test
    @DisplayName("an exp4j formula with variable x overrides the linear mapping")
    void formulaOverridesLinear() {
        assertEquals(127.5, ValueInterpolator.translate(0.5, 0d, 100d, "x * 255"));
        assertEquals(127d, ValueInterpolator.translate(0.5, null, null, "floor(x * 255)"));
    }

    @Test
    @DisplayName("a broken formula falls back to the linear mapping instead of dropping the action")
    void brokenFormulaFallsBack() {
        assertEquals(20d, ValueInterpolator.translate(0.5, 10d, 30d, "x *"));
    }

    @Test
    @DisplayName("{{ value }} is substituted with whole numbers rendered as integers")
    void tokenSubstitution() {
        assertEquals("brightness: 64", ValueInterpolator.interpolate("brightness: {{ value }}", 64));
        assertEquals("brightness: 63.75", ValueInterpolator.interpolate("brightness: {{ value }}", 63.75));
        assertEquals("a: 5, b: 5", ValueInterpolator.interpolate("a: {{value}}, b: {{  value  }}", 5));
    }

    @Test
    @DisplayName("end-to-end: dial position → formula → YAML token → parsed service call body")
    void endToEnd() {
        var action = """
                action: light.turn_on
                target:
                  entity_id: light.living_room
                data:
                  brightness: {{ value }}
                """;
        var yaml = ValueInterpolator.interpolate(action, ValueInterpolator.translate(0.25, null, null, "floor(x * 255)"));
        var parsed = HaActionYaml.parse(yaml);
        assertEquals("light", parsed.domain());
        assertEquals("turn_on", parsed.service());
        assertEquals(63, parsed.data().get("brightness"), "the whole number must arrive as an integer, not 63.0");
        assertEquals("light.living_room", parsed.data().get("entity_id"));
    }
}
