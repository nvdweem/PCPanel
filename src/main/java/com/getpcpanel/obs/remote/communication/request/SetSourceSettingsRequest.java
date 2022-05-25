package com.getpcpanel.obs.remote.communication.request;

import java.util.Map;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetSourceSettingsRequest extends BaseRequest {
    private final String sourceName;

    private final Map<String, Object> sourceSettings;

    public SetSourceSettingsRequest(OBSCommunicator com, String sourceName, Map<String, Object> settings) {
        super(com, RequestType.SetSourceSettings);
        this.sourceName = sourceName;
        sourceSettings = settings;
    }
}

