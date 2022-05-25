package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Source;

public class GetSourcesListResponse extends BaseResponse {
    private List<Source> sources;

    public List<Source> getSources() {
        return sources;
    }
}
