package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Scene;

public class GetSceneListResponse extends BaseResponse {
    private List<Scene> scenes;

    public List<Scene> getScenes() {
        return scenes;
    }
}
