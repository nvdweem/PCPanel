package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Scene;

import lombok.Data;

@Data
public class GetSceneListResponse extends BaseResponse {
    private List<Scene> scenes;
}
