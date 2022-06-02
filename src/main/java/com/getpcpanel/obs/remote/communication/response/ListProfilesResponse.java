package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Profile;

import lombok.Data;

@Data
public class ListProfilesResponse extends BaseResponse {
    private List<Profile> profiles;
}

