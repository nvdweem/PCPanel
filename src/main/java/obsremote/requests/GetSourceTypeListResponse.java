package obsremote.requests;

import java.util.List;

import obsremote.objects.SourceType;

public class GetSourceTypeListResponse extends ResponseBase {
    private List<SourceType> types;

    public List<SourceType> getSourceTypes() {
        return types;
    }
}

