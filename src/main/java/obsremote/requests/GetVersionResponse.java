package obsremote.requests;

import com.google.gson.annotations.SerializedName;

public class GetVersionResponse extends ResponseBase {
    @SerializedName("obs-websocket-version")
    private String obsWebsocketVersion;

    @SerializedName("obs-studio-version")
    private String obsStudioVersion;

    public String getObsWebsocketVersion() {
        return obsWebsocketVersion;
    }

    public String getObsStudioVersion() {
        return obsStudioVersion;
    }
}

