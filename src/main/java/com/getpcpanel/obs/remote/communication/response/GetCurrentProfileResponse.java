package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class GetCurrentProfileResponse extends BaseResponse {
    @SerializedName("profile-name")
    private String profileName;

    public String getProfileName() {
        return profileName;
    }
}
