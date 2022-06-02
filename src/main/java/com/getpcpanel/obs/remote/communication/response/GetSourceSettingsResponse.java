package com.getpcpanel.obs.remote.communication.response;

import java.util.Map;

import lombok.Data;

@Data
public class GetSourceSettingsResponse extends BaseResponse {
    private String sourceName;
    private String sourceType;
    private Map<String, Object> sourceSettings;
}
