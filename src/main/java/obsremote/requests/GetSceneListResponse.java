package obsremote.requests;

import java.util.List;

import obsremote.objects.Scene;

public class GetSceneListResponse extends ResponseBase {
    private List<Scene> scenes;

    public List<Scene> getScenes() {
        return scenes;
    }
}
