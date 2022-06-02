package com.getpcpanel.obs.remote.objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Profile {
    @JsonProperty("profile-name")
    private String name;
}
