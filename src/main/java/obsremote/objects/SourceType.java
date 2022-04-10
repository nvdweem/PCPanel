package obsremote.objects;

public class SourceType {
    private String typeId;

    private String displayName;

    private String type;

    private Capabilities caps;

    public String getDisplayName() {
        return displayName;
    }

    public String getType() {
        return type;
    }

    public String getTypeId() {
        return typeId;
    }

    public Capabilities getCaps() {
        return caps;
    }
}
