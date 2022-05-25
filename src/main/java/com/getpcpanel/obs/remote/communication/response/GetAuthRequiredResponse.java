package com.getpcpanel.obs.remote.communication.response;

public class GetAuthRequiredResponse extends BaseResponse {
    private boolean authRequired;

    private String challenge;

    private String salt;

    public boolean isAuthRequired() {
        return authRequired;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getSalt() {
        return salt;
    }
}
