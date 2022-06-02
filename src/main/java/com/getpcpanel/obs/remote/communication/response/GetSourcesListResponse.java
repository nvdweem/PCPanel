package com.getpcpanel.obs.remote.communication.response;

import java.util.List;

import com.getpcpanel.obs.remote.objects.Source;

import lombok.Data;

@Data
public class GetSourcesListResponse extends BaseResponse {
    private List<Source> sources;
}
