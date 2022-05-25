package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class AuthenticateRequest extends BaseRequest {
    private final String auth;

    public AuthenticateRequest(OBSCommunicator com, String auth) {
        super(com, RequestType.Authenticate);
        this.auth = auth;
    }
}
