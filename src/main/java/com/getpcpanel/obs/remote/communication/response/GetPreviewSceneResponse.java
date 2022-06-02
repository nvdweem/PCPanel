package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Source;

import lombok.Data;

@Data
public class GetPreviewSceneResponse extends BaseResponse {
    private List<Source> sources;
    private String name;
}
