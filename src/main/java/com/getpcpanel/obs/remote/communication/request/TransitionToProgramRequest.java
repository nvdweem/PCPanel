package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class TransitionToProgramRequest extends BaseRequest {
    @JsonProperty("with-transition")
    private WithTransition withTransition;

    @Data
    public static class WithTransition {
        private final String name;
        private final Integer duration;

        public WithTransition(String name, int duration) {
            if (duration == 0) {
                this.duration = null;
            } else {
                this.duration = duration;
            }
            this.name = name;
        }
    }

    public TransitionToProgramRequest(OBSCommunicator com, String transition, int duration) {
        super(com, RequestType.TransitionToProgram);
        withTransition = new WithTransition(transition, duration);
    }
}

