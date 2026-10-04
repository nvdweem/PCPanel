package com.getpcpanel.rest.model.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.getpcpanel.AppLikeMapper;
import com.getpcpanel.StubBeans;
import com.getpcpanel.profile.Save;

/** Keeping the visualizer and notification lights going while locked: saved both ways, off in older files. */
class SettingsDtoWhileLockedTest {
    @Test
    void bothTogglesGoThroughTheSettingsBothWays() {
        var save = new Save();
        save.setVisualizerWhileLocked(true);
        save.setNotificationLightsWhileLocked(true);
        var dto = SettingsDto.from(save);
        assertTrue(dto.isVisualizerWhileLocked());
        assertTrue(dto.isNotificationLightsWhileLocked());

        var target = new Save();
        dto.applyTo(target);
        assertTrue(target.isVisualizerWhileLocked());
        assertTrue(target.isNotificationLightsWhileLocked());

        dto.setVisualizerWhileLocked(false);
        dto.applyTo(target);
        assertFalse(target.isVisualizerWhileLocked());
        assertTrue(target.isNotificationLightsWhileLocked());
    }

    @Test
    void anOlderFileHasBothOff() throws Exception {
        StubBeans.install();
        var save = AppLikeMapper.build().readValue(
                new String(Objects.requireNonNull(getClass().getResourceAsStream("/legacy-saves/profiles-2.0.json")).readAllBytes(), StandardCharsets.UTF_8),
                Save.class);
        assertFalse(save.isVisualizerWhileLocked());
        assertFalse(save.isNotificationLightsWhileLocked());
    }
}
