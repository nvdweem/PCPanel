package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.util.CdiHelper;

import lombok.Getter;
import lombok.ToString;

/**
 * Performs a no-target OBS control action — start/stop/toggle streaming, recording, virtual camera and
 * replay buffer — via the OBS WebSocket. Each {@link ObsActionType} maps to its OBS request name.
 */
@Getter
@ToString(callSuper = true)
public class CommandObsAction extends CommandObs implements ButtonAction {
    private final ObsActionType action;

    @JsonCreator
    public CommandObsAction(@JsonProperty("action") ObsActionType action) {
        this.action = action;
    }

    @Override
    public void execute() {
        if (action == null) {
            return;
        }
        var obs = CdiHelper.getBean(OBS.class);
        if (obs.isConnected()) {
            obs.performAction(action.getRequestType());
        }
    }

    @Override
    public String buildLabel() {
        return "OBS: " + (action == null ? "" : action.getLabel());
    }

    /** OBS WebSocket 5 requests that take no request data. */
    @Getter
    public enum ObsActionType {
        START_STREAM("StartStream", "Start streaming"),
        STOP_STREAM("StopStream", "Stop streaming"),
        TOGGLE_STREAM("ToggleStream", "Toggle streaming"),
        START_RECORD("StartRecord", "Start recording"),
        STOP_RECORD("StopRecord", "Stop recording"),
        TOGGLE_RECORD("ToggleRecord", "Toggle recording"),
        TOGGLE_RECORD_PAUSE("ToggleRecordPause", "Pause / resume recording"),
        START_VIRTUAL_CAM("StartVirtualCam", "Start virtual camera"),
        STOP_VIRTUAL_CAM("StopVirtualCam", "Stop virtual camera"),
        TOGGLE_VIRTUAL_CAM("ToggleVirtualCam", "Toggle virtual camera"),
        TOGGLE_REPLAY_BUFFER("ToggleReplayBuffer", "Toggle replay buffer"),
        SAVE_REPLAY_BUFFER("SaveReplayBuffer", "Save replay buffer");

        private final String requestType;
        private final String label;

        ObsActionType(String requestType, String label) {
            this.requestType = requestType;
            this.label = label;
        }
    }
}
