package com.getpcpanel.obs.remote.callbacks;

import com.getpcpanel.obs.remote.communication.response.BaseResponse;

public interface Callback<T extends BaseResponse> {
    void run(T param);
}
