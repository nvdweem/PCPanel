package obsremote.events;

import com.google.gson.annotations.SerializedName;

public class BaseEvent {
    private EventType eventType;

    public EventType getEventType() {
        return eventType;
    }

    @SerializedName("update-type")
    public void setUpdateType(String updateType) {
        System.out.println(updateType);
        eventType = EventType.valueOf(updateType);
    }
}
