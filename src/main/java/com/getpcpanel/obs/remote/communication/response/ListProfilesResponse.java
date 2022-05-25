package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Profile;

public class ListProfilesResponse extends BaseResponse {
    private List<Profile> profiles;

    public List<Profile> getProfiles() {
        return profiles;
    }
}

