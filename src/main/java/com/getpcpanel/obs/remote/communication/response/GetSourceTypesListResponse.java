package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.SourceType;

import lombok.Data;

@Data
public class GetSourceTypesListResponse extends BaseResponse {
    private List<SourceType> types;
}

