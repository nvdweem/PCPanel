package com.getpcpanel.integration.sonar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getpcpanel.AppLikeMapper;
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
    void defaultsToTwelveUpdatesASecond() {
        assertEquals(12, SonarSettings.DEFAULT.updatesPerSecond());
        assertEquals(12, SonarSettings.DEFAULT_UPDATES_PER_SECOND);
    }

    @Test
    void aSaveWithoutASonarBlockReadsAsTheDefault() throws Exception {
        var save = mapper.readValue("{}", Save.class);

        assertEquals(SonarSettings.DEFAULT, save.getSonar());
    }

    @Test
    void aSonarBlockWithoutARateReadsAsTheDefaultRate() throws Exception {
        var settings = mapper.readValue("{\"enabled\":true}", SonarSettings.class);

        assertTrue(settings.enabled());
        assertEquals(12, settings.updatesPerSecond());
    }

    @Test
    void aNullRateReadsAsTheDefaultRate() throws Exception {
        assertEquals(12, mapper.readValue("{\"enabled\":true,\"updatesPerSecond\":null}", SonarSettings.class).updatesPerSecond());
    }

    @Test
    void anOutOfRangeRateIsClampedIntoSixToTwentyFive() throws Exception {
        assertEquals(6, new SonarSettings(true, 5).updatesPerSecond());
        assertEquals(6, new SonarSettings(true, 0).updatesPerSecond());
        assertEquals(6, new SonarSettings(true, -5).updatesPerSecond());
        assertEquals(25, new SonarSettings(true, 26).updatesPerSecond());
        assertEquals(25, new SonarSettings(true, 99).updatesPerSecond());
        assertEquals(25, new SonarSettings(true, 1_000).updatesPerSecond());
        assertEquals(6, new SonarSettings(true, 6).updatesPerSecond());
        assertEquals(12, new SonarSettings(true, 12).updatesPerSecond());
        assertEquals(20, new SonarSettings(true, 20).updatesPerSecond());
        assertEquals(25, new SonarSettings(true, 25).updatesPerSecond());
        assertEquals(25, mapper.readValue("{\"enabled\":true,\"updatesPerSecond\":99}", SonarSettings.class).updatesPerSecond());
        assertEquals(6, mapper.readValue("{\"enabled\":true,\"updatesPerSecond\":1}", SonarSettings.class).updatesPerSecond());
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        var json = mapper.writeValueAsString(new SonarSettings(true, 9));

        assertEquals("{\"enabled\":true,\"updatesPerSecond\":9}", json);
        assertEquals(new SonarSettings(true, 9), mapper.readValue(json, SonarSettings.class));
    }

    @Test
    void theRateSurvivesASaveRoundTripThroughTheAppMapper() throws Exception {
        var appMapper = AppLikeMapper.build();
        var save = new Save();
        save.setSonar(new SonarSettings(true, 7));

        var read = appMapper.readValue(appMapper.writeValueAsString(save), Save.class);

        assertEquals(new SonarSettings(true, 7), read.getSonar());
    }
}
