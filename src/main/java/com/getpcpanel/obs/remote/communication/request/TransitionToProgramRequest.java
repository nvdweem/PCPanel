package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class TransitionToProgramRequest extends BaseRequest {
    @SerializedName("with-transition")
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
