package util;

import java.io.Serializable;

public class SoundDevice implements Serializable {
    private final String mainName;

    private final String descName;

    private final String id;

    private final SoundDeviceType type;

    public enum SoundDeviceType {
        INPUT, OUTPUT
    }

    public SoundDevice(String mainName, String descName, String id, SoundDeviceType type) {
        this.mainName = mainName;
        this.descName = descName;
        this.id = id;
        this.type = type;
    }

    public boolean isOutput() {
        return type == SoundDeviceType.OUTPUT;
    }

    public boolean isInput() {
        return type == SoundDeviceType.INPUT;
    }

    public String getCombinedName() {
        return mainName + " (" + descName + ")";
    }

    public String getMainName() {
        return mainName;
    }

    public String getDescName() {
        return descName;
    }

    public String getId() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof SoundDevice))
            return false;
        return !(this != obj && !id.equals(((SoundDevice) obj).id));
    }

    public String toString() {
        return getCombinedName();
    }
}

