package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Source;

public class GetPreviewSceneResponse extends BaseResponse {
    private List<Source> sources;

    private String name;

    public List<Source> getSources() {
        return sources;
    }

    public String getName() {
        return name;
    }
}
