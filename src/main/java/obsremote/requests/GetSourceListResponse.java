package obsremote.requests;

import java.util.List;

import obsremote.objects.Source;

public class GetSourceListResponse extends ResponseBase {
    private List<Source> sources;

    public List<Source> getSources() {
        return sources;
    }
}
