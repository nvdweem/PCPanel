package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.integration.sonar.dto.SonarSettings;
import com.getpcpanel.profile.Save;

import org.junit.jupiter.api.Test;

class SonarSettingsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultsToDisabled() {
        assertFalse(SonarSettings.DEFAULT.enabled());
    }

    @Test
    void aSaveWithoutASonarBlockReadsAsTheDefault() throws Exception {
        var save = mapper.readValue("{}", Save.class);

        assertEquals(SonarSettings.DEFAULT, save.getSonar());
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        var json = mapper.writeValueAsString(new SonarSettings(true));

        assertEquals("{\"enabled\":true}", json);
        assertTrue(mapper.readValue(json, SonarSettings.class).enabled());
    }
}
