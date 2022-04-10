package obsremote.requests;

import java.util.List;

import obsremote.objects.Source;

public class GetPreviewSceneResponse extends ResponseBase {
    private List<Source> sources;

    private String name;

    public List<Source> getSources() {
        return sources;
    }

    public String getName() {
        return name;
    }
}
