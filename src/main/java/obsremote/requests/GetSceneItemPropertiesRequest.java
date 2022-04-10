package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class GetSceneItemPropertiesRequest extends BaseRequest {
    private String scene;
    private final Item item;

    private record Item(String id, String name) {
    }

    public GetSceneItemPropertiesRequest(OBSCommunicator com, String scene, String source) {
        super(com, RequestType.GetSceneItemProperties);
        this.scene = scene;
        item = new Item(null, source);
    }
}
