package obsremote.events.responses;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import obsremote.objects.Source;
import obsremote.requests.ResponseBase;

public class SwitchScenesResponse extends ResponseBase {
    @SerializedName("scene-name")
    private String sceneName;

    private List<Source> sources;

    public String getSceneName() {
        return sceneName;
    }

    public List<Source> getSources() {
        return sources;
    }
}
