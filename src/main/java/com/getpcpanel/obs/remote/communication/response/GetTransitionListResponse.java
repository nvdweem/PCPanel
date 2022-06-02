package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetTransitionListResponse extends BaseResponse {
    @JsonProperty("current-transition")
    private String currentTransition;
    private List<Transition> transitions;

    @Data
    public static class Transition {
        private String name;
    }
}

