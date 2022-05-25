package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.SourceType;

public class GetSourceTypesListResponse extends BaseResponse {
    private List<SourceType> types;

    public List<SourceType> getSourceTypes() {
        return types;
    }
}

