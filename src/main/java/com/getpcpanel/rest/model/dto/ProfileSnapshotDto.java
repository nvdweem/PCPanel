package com.getpcpanel.rest.model.dto;

import java.util.Map;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.dto.KnobSetting;

/**
 * Snapshot of the currently active profile — all assignment data the frontend
 * needs to render the device page without any additional HTTP calls.
 */
public record ProfileSnapshotDto(
        String name,
        Map<Integer, Commands> dialData,
        Map<Integer, Commands> buttonData,
        Map<Integer, Commands> dblButtonData,
        Map<Integer, KnobSetting> knobSettings
) {
    public static ProfileSnapshotDto from(Profile profile) {
        return new ProfileSnapshotDto(
                profile.getName(),
                profile.getDialData(),
                profile.getButtonData(),
                profile.getDblButtonData(),
                profile.getKnobSettings()
        );
    }
}
