package com.getpcpanel.obs.remote.communication.response;

import lombok.Data;

@Data
public class GetAuthRequiredResponse extends BaseResponse {
    private boolean authRequired;
    private String challenge;
    private String salt;
}
